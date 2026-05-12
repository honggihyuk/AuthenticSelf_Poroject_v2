import React, { useState } from 'react';
import {
  View, Text, Pressable, Image, StyleSheet, ActivityIndicator, Alert,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { NativeStackScreenProps } from '@react-navigation/native-stack';

import { RootStackParamList } from '../../App';
import { settings } from '../settings';
import { uploadPhoto, UploadFailedError } from '../api/client';
import { messageForCode } from '../api/errorMessages';

type Props = NativeStackScreenProps<RootStackParamList, 'Upload'>;

type PickedAsset = {
  uri: string;
  fileName: string;
  mimeType: string;
  fileSize?: number;
};

/**
 * UploadScreen — image picker + preview + POST to
 * `/api/v1/spaces/photo`. Matches FR-7 / AC-17–AC-19.
 */
export default function UploadScreen({ navigation }: Props) {
  const [asset, setAsset]         = useState<PickedAsset | null>(null);
  const [uploading, setUploading] = useState<boolean>(false);
  const [progress, setProgress]   = useState<number>(0);

  const pickImage = async () => {
    const res = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsEditing: false,
      quality: 1,
    });
    if (res.canceled || res.assets.length === 0) return;
    const a = res.assets[0];
    setAsset({
      uri:       a.uri,
      fileName:  a.fileName ?? `photo_${Date.now()}.jpg`,
      mimeType:  a.mimeType ?? 'image/jpeg',
      fileSize:  a.fileSize,
    });
  };

  const submit = async () => {
    if (!asset) return;
    setUploading(true);
    setProgress(0);
    try {
      const result = await uploadPhoto({
        baseUrl:   settings.apiBaseUrl,
        userId:    settings.userId,
        uri:       asset.uri,
        fileName:  asset.fileName,
        mimeType:  asset.mimeType,
        onProgress: (p) => setProgress(p),
      });
      navigation.replace('Analyzing', { roomId: result.roomId, photoUri: asset.uri });
    } catch (err) {
      const code = err instanceof UploadFailedError ? err.body?.errorCode : undefined;
      Alert.alert(
        '업로드 실패',
        messageForCode(code),
        [{ text: '다시 시도', onPress: () => { setAsset(null); } }],
      );
    } finally {
      setUploading(false);
    }
  };

  return (
    <View style={styles.container}>
      {asset ? (
        <Image testID="preview" source={{ uri: asset.uri }} style={styles.preview} />
      ) : (
        <Pressable testID="btn-pick" style={styles.picker} onPress={pickImage}>
          <Text style={styles.pickerLabel}>사진 선택</Text>
        </Pressable>
      )}
      {asset && asset.fileSize != null && (
        <Text style={styles.meta}>{(asset.fileSize / 1024 / 1024).toFixed(2)} MB</Text>
      )}
      {asset && !uploading && (
        <Pressable testID="btn-upload" style={styles.submit} onPress={submit}>
          <Text style={styles.submitLabel}>업로드</Text>
        </Pressable>
      )}
      {uploading && (
        <View testID="upload-progress" style={styles.progressRow}>
          <ActivityIndicator />
          <Text style={styles.progressLabel}>{progress}%</Text>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container:      { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  picker:         {
    width: 220, height: 220, borderRadius: 12, borderWidth: 2,
    borderColor: '#1f6feb', alignItems: 'center', justifyContent: 'center',
  },
  pickerLabel:    { color: '#1f6feb', fontSize: 16, fontWeight: '600' },
  preview:        { width: 220, height: 220, borderRadius: 12, resizeMode: 'cover' },
  meta:           { marginTop: 12, color: '#555' },
  submit:         {
    marginTop: 24, backgroundColor: '#1f6feb', paddingVertical: 14,
    paddingHorizontal: 32, borderRadius: 12,
  },
  submitLabel:    { color: 'white', fontSize: 16, fontWeight: '600' },
  progressRow:    { marginTop: 24, flexDirection: 'row', alignItems: 'center' },
  progressLabel:  { marginLeft: 12, fontSize: 16 },
});
