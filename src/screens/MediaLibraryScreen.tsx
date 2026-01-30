/**
 * Media Library Screen - Browse media items
 */

import React, {useState, useEffect} from 'react';
import {
  SafeAreaView,
  FlatList,
  StatusBar,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  Alert,
  useColorScheme,
  ActivityIndicator,
} from 'react-native';
import {useNavigation} from '@react-navigation/native';
import {NativeStackNavigationProp} from '@react-navigation/native-stack';

import {Colors} from '../styles/Colors';
import JellyfinApiService from '../services/JellyfinApiService';
import {RootStackParamList} from '../types/navigation';
import {MediaItem} from '../types/media';
import {formatTicks} from '../utils/timeUtils';

type MediaLibraryNavigationProp = NativeStackNavigationProp<
  RootStackParamList,
  'MediaLibrary'
>;

function MediaLibraryScreen(): React.JSX.Element {
  const navigation = useNavigation<MediaLibraryNavigationProp>();
  const isDarkMode = useColorScheme() === 'dark';
  const [mediaItems, setMediaItems] = useState<MediaItem[]>([]);
  const [loading, setLoading] = useState(true);

  const backgroundStyle = {
    backgroundColor: isDarkMode ? Colors.darker : Colors.lighter,
    flex: 1,
  };

  useEffect(() => {
    loadMediaItems();
  }, []);

  const loadMediaItems = async () => {
    setLoading(true);
    try {
      const result = await JellyfinApiService.getMediaItems();
      if (result.success && result.data) {
        setMediaItems(result.data);
      } else {
        Alert.alert('Error', result.error || 'Failed to load media items');
      }
    } catch (error) {
      Alert.alert('Error', 'Failed to load media items');
    } finally {
      setLoading(false);
    }
  };

  const handleMediaItemPress = (item: MediaItem) => {
    // Navigate to segment list for this item
    navigation.navigate('SegmentList', {
      itemId: item.Id,
      itemName: item.Name,
    });
  };

  const getMediaItemTitle = (item: MediaItem): string => {
    if (item.Type === 'Episode' && item.SeriesName) {
      const season = item.ParentIndexNumber
        ? `S${item.ParentIndexNumber}`
        : '';
      const episode = item.IndexNumber ? `E${item.IndexNumber}` : '';
      return `${item.SeriesName} ${season}${episode} - ${item.Name}`;
    }
    return item.Name;
  };

  const renderMediaItem = ({item}: {item: MediaItem}) => (
    <TouchableOpacity
      style={[
        styles.mediaItem,
        {backgroundColor: isDarkMode ? Colors.dark : Colors.white},
      ]}
      onPress={() => handleMediaItemPress(item)}>
      <View style={styles.mediaItemContent}>
        <Text
          style={[
            styles.mediaTitle,
            {color: isDarkMode ? Colors.white : Colors.black},
          ]}>
          {getMediaItemTitle(item)}
        </Text>
        {item.RunTimeTicks && (
          <Text
            style={[styles.mediaInfo, {color: isDarkMode ? Colors.light : Colors.dark}]}>
            Duration: {formatTicks(item.RunTimeTicks)}
          </Text>
        )}
        <Text
          style={[styles.mediaType, {color: isDarkMode ? Colors.light : Colors.dark}]}>
          {item.Type}
        </Text>
      </View>
      <Text style={[styles.arrow, {color: isDarkMode ? Colors.light : Colors.dark}]}>
        ›
      </Text>
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={backgroundStyle}>
      <StatusBar
        barStyle={isDarkMode ? 'light-content' : 'dark-content'}
        backgroundColor={backgroundStyle.backgroundColor}
      />
      <View style={[styles.container, backgroundStyle]}>
        <View style={styles.header}>
          <Text
            style={[
              styles.title,
              {color: isDarkMode ? Colors.white : Colors.black},
            ]}>
            Media Library
          </Text>
          <TouchableOpacity
            style={styles.refreshButton}
            onPress={loadMediaItems}>
            <Text style={styles.refreshButtonText}>↻</Text>
          </TouchableOpacity>
        </View>

        {loading ? (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color={Colors.primary} />
            <Text style={[styles.loadingText, {color: isDarkMode ? Colors.light : Colors.dark}]}>
              Loading media items...
            </Text>
          </View>
        ) : mediaItems.length === 0 ? (
          <View style={styles.emptyContainer}>
            <Text style={[styles.emptyText, {color: isDarkMode ? Colors.light : Colors.dark}]}>
              No media items found
            </Text>
          </View>
        ) : (
          <FlatList
            data={mediaItems}
            renderItem={renderMediaItem}
            keyExtractor={item => item.Id}
            contentContainerStyle={styles.listContent}
          />
        )}
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
    paddingBottom: 8,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
  },
  refreshButton: {
    width: 40,
    height: 40,
    justifyContent: 'center',
    alignItems: 'center',
  },
  refreshButtonText: {
    fontSize: 28,
    color: Colors.primary,
  },
  listContent: {
    padding: 16,
    paddingTop: 8,
  },
  mediaItem: {
    flexDirection: 'row',
    padding: 16,
    marginBottom: 12,
    borderRadius: 8,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 2},
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  mediaItemContent: {
    flex: 1,
  },
  mediaTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 4,
  },
  mediaInfo: {
    fontSize: 14,
    marginBottom: 2,
  },
  mediaType: {
    fontSize: 12,
    fontStyle: 'italic',
  },
  arrow: {
    fontSize: 28,
    marginLeft: 8,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 16,
    fontSize: 16,
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  emptyText: {
    fontSize: 16,
  },
});

export default MediaLibraryScreen;
