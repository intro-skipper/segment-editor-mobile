/**
 * Segment List Screen - View and manage segments
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
import {useNavigation, useRoute, RouteProp} from '@react-navigation/native';
import {NativeStackNavigationProp} from '@react-navigation/native-stack';

import {Colors} from '../styles/Colors';
import JellyfinApiService, {Segment, SegmentType} from '../services/JellyfinApiService';
import {RootStackParamList} from '../types/navigation';
import {formatTicks} from '../utils/timeUtils';

type SegmentListNavigationProp = NativeStackNavigationProp<
  RootStackParamList,
  'SegmentList'
>;
type SegmentListRouteProp = RouteProp<RootStackParamList, 'SegmentList'>;

function SegmentListScreen(): React.JSX.Element {
  const navigation = useNavigation<SegmentListNavigationProp>();
  const route = useRoute<SegmentListRouteProp>();
  const {itemId, itemName} = route.params;

  const isDarkMode = useColorScheme() === 'dark';
  const [segments, setSegments] = useState<Segment[]>([]);
  const [loading, setLoading] = useState(true);

  const backgroundStyle = {
    backgroundColor: isDarkMode ? Colors.darker : Colors.lighter,
    flex: 1,
  };

  useEffect(() => {
    loadSegments();
  }, [itemId]);

  const loadSegments = async () => {
    setLoading(true);
    try {
      const result = await JellyfinApiService.getSegments(itemId);
      if (result.success && result.data) {
        setSegments(result.data);
      } else {
        Alert.alert('Error', result.error || 'Failed to load segments');
      }
    } catch (error) {
      Alert.alert('Error', 'Failed to load segments');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateSegment = () => {
    navigation.navigate('SegmentEditor', {
      itemId,
      itemName,
    });
  };

  const handleEditSegment = (segment: Segment) => {
    navigation.navigate('SegmentEditor', {
      itemId,
      itemName,
      segmentType: segment.Type,
      startTicks: segment.StartTicks,
      endTicks: segment.EndTicks,
    });
  };

  const handleDeleteSegment = (segment: Segment) => {
    Alert.alert(
      'Delete Segment',
      `Are you sure you want to delete this ${segment.Type} segment?`,
      [
        {text: 'Cancel', style: 'cancel'},
        {
          text: 'Delete',
          style: 'destructive',
          onPress: async () => {
            const result = await JellyfinApiService.deleteSegment(
              itemId,
              segment.Type,
            );
            if (result.success) {
              Alert.alert('Success', 'Segment deleted successfully');
              loadSegments();
            } else {
              Alert.alert('Error', result.error || 'Failed to delete segment');
            }
          },
        },
      ],
    );
  };

  const handleOpenVideoPlayer = () => {
    const videoUrl = JellyfinApiService.getVideoUrl(itemId);
    navigation.navigate('VideoPlayer', {
      itemId,
      itemName,
      videoUrl,
    });
  };

  const getSegmentTypeColor = (type: SegmentType): string => {
    switch (type) {
      case SegmentType.Intro:
        return Colors.primary;
      case SegmentType.Outro:
        return Colors.secondary;
      case SegmentType.Recap:
        return Colors.warning;
      case SegmentType.Preview:
        return Colors.danger;
      case SegmentType.Credits:
        return Colors.success;
      default:
        return Colors.dark;
    }
  };

  const renderSegment = ({item}: {item: Segment}) => (
    <View
      style={[
        styles.segmentItem,
        {backgroundColor: isDarkMode ? Colors.dark : Colors.white},
      ]}>
      <View style={styles.segmentHeader}>
        <View style={[styles.segmentTypeBadge, {backgroundColor: getSegmentTypeColor(item.Type)}]}>
          <Text style={styles.segmentTypeText}>{item.Type}</Text>
        </View>
        <View style={styles.segmentActions}>
          <TouchableOpacity
            onPress={() => handleEditSegment(item)}
            style={styles.actionButton}>
            <Text style={styles.actionButtonText}>Edit</Text>
          </TouchableOpacity>
          <TouchableOpacity
            onPress={() => handleDeleteSegment(item)}
            style={[styles.actionButton, styles.deleteButton]}>
            <Text style={styles.actionButtonText}>Delete</Text>
          </TouchableOpacity>
        </View>
      </View>
      <View style={styles.segmentTimes}>
        <Text style={[styles.timeText, {color: isDarkMode ? Colors.light : Colors.dark}]}>
          Start: {formatTicks(item.StartTicks)}
        </Text>
        <Text style={[styles.timeText, {color: isDarkMode ? Colors.light : Colors.dark}]}>
          End: {formatTicks(item.EndTicks)}
        </Text>
        <Text style={[styles.timeText, {color: isDarkMode ? Colors.light : Colors.dark}]}>
          Duration: {formatTicks(item.EndTicks - item.StartTicks)}
        </Text>
      </View>
    </View>
  );

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
            Segments
          </Text>
          <TouchableOpacity onPress={loadSegments}>
            <Text style={styles.refreshButton}>↻</Text>
          </TouchableOpacity>
        </View>

        <Text
          numberOfLines={2}
          style={[
            styles.subtitle,
            {color: isDarkMode ? Colors.light : Colors.dark},
          ]}>
          {itemName}
        </Text>

        <View style={styles.actionBar}>
          <TouchableOpacity
            style={[styles.button, styles.primaryButton]}
            onPress={handleCreateSegment}>
            <Text style={styles.buttonText}>+ Create Segment</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.button, styles.secondaryButton]}
            onPress={handleOpenVideoPlayer}>
            <Text style={styles.buttonText}>▶ Play Video</Text>
          </TouchableOpacity>
        </View>

        {loading ? (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color={Colors.primary} />
            <Text style={[styles.loadingText, {color: isDarkMode ? Colors.light : Colors.dark}]}>
              Loading segments...
            </Text>
          </View>
        ) : segments.length === 0 ? (
          <View style={styles.emptyContainer}>
            <Text style={[styles.emptyText, {color: isDarkMode ? Colors.light : Colors.dark}]}>
              No segments found for this media item
            </Text>
            <Text style={[styles.emptySubtext, {color: isDarkMode ? Colors.light : Colors.dark}]}>
              Create a new segment to get started
            </Text>
          </View>
        ) : (
          <FlatList
            data={segments}
            renderItem={renderSegment}
            keyExtractor={(item) => `${item.ItemId}-${item.Type}`}
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
  backButton: {
    fontSize: 24,
    color: Colors.primary,
    fontWeight: 'bold',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    flex: 1,
    textAlign: 'center',
    marginHorizontal: 16,
  },
  refreshButton: {
    fontSize: 24,
    color: Colors.primary,
  },
  subtitle: {
    fontSize: 16,
    paddingHorizontal: 16,
    marginBottom: 16,
    textAlign: 'center',
  },
  actionBar: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    marginBottom: 16,
    gap: 12,
  },
  button: {
    flex: 1,
    padding: 12,
    borderRadius: 8,
    alignItems: 'center',
  },
  primaryButton: {
    backgroundColor: Colors.primary,
  },
  secondaryButton: {
    backgroundColor: Colors.secondary,
  },
  buttonText: {
    color: Colors.white,
    fontSize: 14,
    fontWeight: '600',
  },
  listContent: {
    padding: 16,
    paddingTop: 0,
  },
  segmentItem: {
    padding: 16,
    marginBottom: 12,
    borderRadius: 8,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 2},
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  segmentHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  segmentTypeBadge: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
  },
  segmentTypeText: {
    color: Colors.white,
    fontSize: 14,
    fontWeight: 'bold',
  },
  segmentActions: {
    flexDirection: 'row',
    gap: 8,
  },
  actionButton: {
    backgroundColor: Colors.primary,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
  },
  deleteButton: {
    backgroundColor: Colors.danger,
  },
  actionButtonText: {
    color: Colors.white,
    fontSize: 12,
    fontWeight: '600',
  },
  segmentTimes: {
    gap: 4,
  },
  timeText: {
    fontSize: 14,
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
    paddingHorizontal: 32,
  },
  emptyText: {
    fontSize: 16,
    textAlign: 'center',
    marginBottom: 8,
  },
  emptySubtext: {
    fontSize: 14,
    textAlign: 'center',
  },
});

export default SegmentListScreen;
