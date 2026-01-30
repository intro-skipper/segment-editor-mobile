/**
 * Video Player Screen - Play video with timestamp controls
 */

import React, {useState, useRef, useEffect} from 'react';
import {
  SafeAreaView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  useColorScheme,
  Alert,
  StatusBar,
} from 'react-native';
import {useNavigation, useRoute, RouteProp} from '@react-navigation/native';
import Video, {
  OnLoadData,
  OnProgressData,
  VideoRef,
} from 'react-native-video';
import {NativeStackNavigationProp} from '@react-navigation/native-stack';

import {Colors} from '../styles/Colors';
import {RootStackParamList} from '../types/navigation';
import {formatTime, copyToClipboard, secondsToTicks} from '../utils/timeUtils';
import JellyfinApiService from '../services/JellyfinApiService';

type VideoPlayerNavigationProp = NativeStackNavigationProp<
  RootStackParamList,
  'VideoPlayer'
>;
type VideoPlayerRouteProp = RouteProp<RootStackParamList, 'VideoPlayer'>;

function VideoPlayerScreen(): React.JSX.Element {
  const navigation = useNavigation<VideoPlayerNavigationProp>();
  const route = useRoute<VideoPlayerRouteProp>();
  const {itemId, itemName, videoUrl} = route.params;

  const isDarkMode = useColorScheme() === 'dark';
  const videoRef = useRef<VideoRef>(null);

  const [paused, setPaused] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [playbackRate, setPlaybackRate] = useState(1.0);
  const [videoError, setVideoError] = useState<string | null>(null);

  const backgroundStyle = {
    backgroundColor: isDarkMode ? Colors.darker : Colors.lighter,
    flex: 1,
  };

  const onLoad = (data: OnLoadData) => {
    setDuration(data.duration);
    setVideoError(null);
  };

  const onProgress = (data: OnProgressData) => {
    setCurrentTime(data.currentTime);
  };

  const onError = (error: any) => {
    console.error('Video player error:', error);
    setVideoError('Failed to load video. Please check your connection and try again.');
    Alert.alert(
      'Video Error',
      'Failed to load video. Please check your connection and try again.',
    );
  };

  const handlePlayPause = () => {
    setPaused(!paused);
  };

  const handleSeek = (seconds: number) => {
    const newTime = Math.max(0, Math.min(currentTime + seconds, duration));
    videoRef.current?.seek(newTime);
  };

  const handleCopyTimestamp = async () => {
    const timestamp = formatTime(currentTime);
    await copyToClipboard(timestamp, `Timestamp copied: ${timestamp}`);
  };

  const handleCopyTicks = async () => {
    const ticks = secondsToTicks(currentTime);
    await copyToClipboard(ticks.toString(), `Ticks copied: ${ticks}`);
  };

  const togglePlaybackSpeed = () => {
    const speeds = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0];
    const currentIndex = speeds.indexOf(playbackRate);
    const nextIndex = (currentIndex + 1) % speeds.length;
    setPlaybackRate(speeds[nextIndex]);
  };

  const handleOpenSegments = () => {
    navigation.navigate('SegmentList', {
      itemId,
      itemName,
    });
  };

  return (
    <SafeAreaView style={backgroundStyle}>
      <StatusBar
        barStyle={isDarkMode ? 'light-content' : 'dark-content'}
        backgroundColor={backgroundStyle.backgroundColor}
      />
      <View style={[styles.container, backgroundStyle]}>
        <View style={styles.header}>
          <TouchableOpacity onPress={() => navigation.goBack()}>
            <Text style={styles.backButton}>‹ Back</Text>
          </TouchableOpacity>
          <Text
            numberOfLines={1}
            style={[
              styles.title,
              {color: isDarkMode ? Colors.white : Colors.black},
            ]}>
            {itemName}
          </Text>
          <TouchableOpacity onPress={handleOpenSegments}>
            <Text style={styles.segmentsButton}>Segments</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.videoContainer}>
          {videoError ? (
            <View style={styles.errorContainer}>
              <Text style={styles.errorText}>⚠️</Text>
              <Text style={styles.errorMessage}>{videoError}</Text>
              <TouchableOpacity
                style={styles.retryButton}
                onPress={() => {
                  setVideoError(null);
                  setPaused(false);
                }}>
                <Text style={styles.retryButtonText}>Retry</Text>
              </TouchableOpacity>
            </View>
          ) : (
            <Video
              ref={videoRef}
              source={{uri: videoUrl}}
              style={styles.video}
              controls={false}
              paused={paused}
              onLoad={onLoad}
              onProgress={onProgress}
              onError={onError}
              rate={playbackRate}
              resizeMode="contain"
            />
          )}
        </View>

        <View
          style={[
            styles.timestampContainer,
            {backgroundColor: isDarkMode ? Colors.dark : Colors.white},
          ]}>
          <Text
            style={[
              styles.timestamp,
              {color: isDarkMode ? Colors.white : Colors.black},
            ]}>
            {formatTime(currentTime)} / {formatTime(duration)}
          </Text>
          <View style={styles.timestampButtons}>
            <TouchableOpacity
              style={styles.copyButton}
              onPress={handleCopyTimestamp}>
              <Text style={styles.copyButtonText}>Copy Time</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.copyButton}
              onPress={handleCopyTicks}>
              <Text style={styles.copyButtonText}>Copy Ticks</Text>
            </TouchableOpacity>
          </View>
        </View>

        <View style={styles.controlsContainer}>
          <View style={styles.seekButtons}>
            <TouchableOpacity
              style={styles.seekButton}
              onPress={() => handleSeek(-10)}>
              <Text style={styles.seekButtonText}>-10s</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.seekButton}
              onPress={() => handleSeek(-5)}>
              <Text style={styles.seekButtonText}>-5s</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.playButton, paused && styles.playButtonActive]}
              onPress={handlePlayPause}>
              <Text style={styles.playButtonText}>{paused ? '▶' : '⏸'}</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.seekButton}
              onPress={() => handleSeek(5)}>
              <Text style={styles.seekButtonText}>+5s</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.seekButton}
              onPress={() => handleSeek(10)}>
              <Text style={styles.seekButtonText}>+10s</Text>
            </TouchableOpacity>
          </View>

          <TouchableOpacity
            style={styles.speedButton}
            onPress={togglePlaybackSpeed}>
            <Text style={styles.speedButtonText}>{playbackRate}x</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.progressBarContainer}>
          <View style={styles.progressBar}>
            <View
              style={[
                styles.progressFill,
                {width: duration > 0 ? `${(currentTime / duration) * 100}%` : '0%'},
              ]}
            />
          </View>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
  },
  backButton: {
    fontSize: 24,
    color: Colors.primary,
    fontWeight: 'bold',
  },
  title: {
    fontSize: 18,
    fontWeight: '600',
    flex: 1,
    textAlign: 'center',
    marginHorizontal: 16,
  },
  segmentsButton: {
    fontSize: 16,
    color: Colors.primary,
    fontWeight: '600',
  },
  videoContainer: {
    width: '100%',
    aspectRatio: 16 / 9,
    backgroundColor: '#000',
  },
  video: {
    width: '100%',
    height: '100%',
  },
  timestampContainer: {
    padding: 16,
    marginTop: 16,
    marginHorizontal: 16,
    borderRadius: 8,
    alignItems: 'center',
  },
  timestamp: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 12,
  },
  timestampButtons: {
    flexDirection: 'row',
    gap: 12,
  },
  copyButton: {
    backgroundColor: Colors.secondary,
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 6,
  },
  copyButtonText: {
    color: Colors.white,
    fontSize: 14,
    fontWeight: '600',
  },
  controlsContainer: {
    padding: 16,
    alignItems: 'center',
  },
  seekButtons: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
    gap: 12,
  },
  seekButton: {
    backgroundColor: Colors.primary,
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 6,
  },
  seekButtonText: {
    color: Colors.white,
    fontSize: 14,
    fontWeight: '600',
  },
  playButton: {
    backgroundColor: Colors.success,
    width: 60,
    height: 60,
    borderRadius: 30,
    justifyContent: 'center',
    alignItems: 'center',
    marginHorizontal: 8,
  },
  playButtonActive: {
    backgroundColor: Colors.primary,
  },
  playButtonText: {
    color: Colors.white,
    fontSize: 24,
  },
  speedButton: {
    backgroundColor: Colors.warning,
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 6,
  },
  speedButtonText: {
    color: Colors.white,
    fontSize: 16,
    fontWeight: '600',
  },
  progressBarContainer: {
    paddingHorizontal: 16,
    marginTop: 8,
  },
  progressBar: {
    height: 4,
    backgroundColor: '#E0E0E0',
    borderRadius: 2,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    backgroundColor: Colors.primary,
  },
  errorContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#000',
    padding: 20,
  },
  errorText: {
    fontSize: 48,
    marginBottom: 16,
  },
  errorMessage: {
    color: '#FFF',
    fontSize: 16,
    textAlign: 'center',
    marginBottom: 20,
  },
  retryButton: {
    backgroundColor: Colors.primary,
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 6,
  },
  retryButtonText: {
    color: Colors.white,
    fontSize: 16,
    fontWeight: '600',
  },
});

export default VideoPlayerScreen;
