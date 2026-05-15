import React, { useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Image,
  Pressable,
  SafeAreaView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { NativeStackScreenProps } from '@react-navigation/native-stack';

import { RootStackParamList } from '../../App';
import { settings } from '../settings';
import { uploadPhoto, UploadFailedError } from '../api/client';
import { messageForCode } from '../api/errorMessages';
import { Button, ScreenHeader } from '../components';
import { colors, radii, spacing, typography } from '../theme';

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
    <SafeAreaView style={styles.safe}>
      <ScreenHeader
        eyebrow="STEP 1"
        title="방 사진을 골라주세요"
        subtitle="가구가 보이는 한 장이면 충분합니다."
      />

      <View style={styles.body}>
        {asset ? (
          <View style={styles.previewWrap}>
            <Image testID="preview" source={{ uri: asset.uri }} style={styles.preview} />
          </View>
        ) : (
          <Pressable
            testID="btn-pick"
            style={({ pressed }) => [styles.picker, pressed && styles.pickerPressed]}
            onPress={pickImage}
            accessibilityRole="button"
            accessibilityLabel="사진 선택"
          >
            <Text style={styles.pickerHint}>탭하여 갤러리에서</Text>
            <Text style={styles.pickerLabel}>사진 선택</Text>
          </Pressable>
        )}

        {asset && asset.fileSize != null && (
          <Text style={styles.meta}>{(asset.fileSize / 1024 / 1024).toFixed(2)} MB</Text>
        )}
      </View>

      <View style={styles.footer}>
        {asset && !uploading && (
          <Button
            testID="btn-upload"
            label="업로드"
            variant="primary"
            size="lg"
            fullWidth
            onPress={submit}
          />
        )}
        {uploading && (
          <View testID="upload-progress" style={styles.progressRow}>
            <ActivityIndicator color={colors.primary} />
            <Text style={styles.progressLabel}>{progress}%</Text>
          </View>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  body: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: spacing.lg,
  },
  picker: {
    width: 260, height: 260,
    borderRadius: radii.lg,
    borderWidth: 2, borderStyle: 'dashed', borderColor: colors.primary,
    backgroundColor: colors.surface,
    alignItems: 'center', justifyContent: 'center',
  },
  pickerPressed: { backgroundColor: colors.primarySoft },
  pickerHint: { ...typography.caption, color: colors.textMuted, marginBottom: spacing.xs },
  pickerLabel: { ...typography.titleM, color: colors.primary },
  previewWrap: {
    borderRadius: radii.lg,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  preview: { width: 260, height: 260, resizeMode: 'cover' },
  meta: { ...typography.bodyM, color: colors.textMuted, marginTop: spacing.sm },

  footer: {
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.xl,
    paddingTop: spacing.md,
  },
  progressRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    height: 56,
  },
  progressLabel: { ...typography.titleM, color: colors.textPrimary, marginLeft: spacing.sm },
});
