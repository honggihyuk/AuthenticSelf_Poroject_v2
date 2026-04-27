// Standard Expo babel config; needed so RN / Jest pick up the right presets.
module.exports = function (api) {
  api.cache(true);
  return {
    presets: ['babel-preset-expo'],
  };
};
