/**
 * Segment Editor Screen - Create/Edit segments
 */

import React, {useState, useEffect} from 'react';
import {
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
  Alert,
  useColorScheme,
} from 'react-native';
import {useNavigation, useRoute, RouteProp} from '@react-navigation/native';
import {NativeStackNavigationProp} from '@react-navigation/native-stack';

import {Colors} from '../styles/Colors';
import JellyfinApiService, {SegmentType, Segment} from '../services/JellyfinApiService';
import {RootStackParamList} from '../types/navigation';
import {
  formatTime,
  parseTimeToSeconds,
  secondsToTicks,
  ticksToSeconds,
} from '../utils/timeUtils';

type SegmentEditorNavigationProp = NativeStackNavigationProp<
  RootStackParamList,
  'SegmentEditor'
>;
type SegmentEditorRouteProp = RouteProp<RootStackParamList, 'SegmentEditor'>;

function SegmentEditorScreen(): React.JSX.Element {
  const navigation = useNavigation<SegmentEditorNavigationProp>();
  const route = useRoute<SegmentEditorRouteProp>();
  const {itemId, itemName, segmentType, startTicks, endTicks} = route.params;

  const isDarkMode = useColorScheme() === 'dark';
  const isEditing = segmentType !== undefined;

  const [selectedType, setSelectedType] = useState<SegmentType>(
    segmentType || SegmentType.Intro,
  );
  const [startTime, setStartTime] = useState(
    startTicks ? formatTime(ticksToSeconds(startTicks)) : '00:00',
  );
  const [endTime, setEndTime] = useState(
    endTicks ? formatTime(ticksToSeconds(endTicks)) : '00:00',
  );
  const [saving, setSaving] = useState(false);

  const backgroundStyle = {
    backgroundColor: isDarkMode ? Colors.darker : Colors.lighter,
    flex: 1,
  };

  const segmentTypes = [
    SegmentType.Intro,
    SegmentType.Outro,
    SegmentType.Recap,
    SegmentType.Preview,
    SegmentType.Credits,
  ];

  const handleSave = async () => {
    // Validate times
    const startSeconds = parseTimeToSeconds(startTime);
    const endSeconds = parseTimeToSeconds(endTime);

    if (startSeconds >= endSeconds) {
      Alert.alert('Error', 'Start time must be before end time');
      return;
    }

    if (startSeconds < 0 || endSeconds < 0) {
      Alert.alert('Error', 'Invalid time format');
      return;
    }

    setSaving(true);

    const segment: Segment = {
      ItemId: itemId,
      Type: selectedType,
      StartTicks: secondsToTicks(startSeconds),
      EndTicks: secondsToTicks(endSeconds),
    };

    try {
      let result;
      if (isEditing) {
        result = await JellyfinApiService.updateSegment(
          itemId,
          selectedType,
          segment,
        );
      } else {
        result = await JellyfinApiService.createSegment(segment);
      }

      if (result.success) {
        Alert.alert(
          'Success',
          `Segment ${isEditing ? 'updated' : 'created'} successfully`,
          [
            {
              text: 'OK',
              onPress: () => navigation.goBack(),
            },
          ],
        );
      } else {
        Alert.alert('Error', result.error || `Failed to ${isEditing ? 'update' : 'create'} segment`);
      }
    } catch (error) {
      Alert.alert('Error', `Failed to ${isEditing ? 'update' : 'create'} segment`);
    } finally {
      setSaving(false);
    }
  };

  const renderSegmentTypeButton = (type: SegmentType) => (
    <TouchableOpacity
      key={type}
      style={[
        styles.typeButton,
        selectedType === type && styles.typeButtonSelected,
        {
          borderColor: isDarkMode ? Colors.light : Colors.dark,
        },
      ]}
      onPress={() => !isEditing && setSelectedType(type)}>
      <Text
        style={[
          styles.typeButtonText,
          selectedType === type && styles.typeButtonTextSelected,
          {color: selectedType === type ? Colors.white : (isDarkMode ? Colors.light : Colors.dark)},
        ]}>
        {type}
      </Text>
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={backgroundStyle}>
      <StatusBar
        barStyle={isDarkMode ? 'light-content' : 'dark-content'}
        backgroundColor={backgroundStyle.backgroundColor}
      />
      <ScrollView
        contentInsetAdjustmentBehavior="automatic"
        style={backgroundStyle}>
        <View style={[styles.container, backgroundStyle]}>
          <View style={styles.header}>
            <TouchableOpacity onPress={() => navigation.goBack()}>
              <Text style={styles.backButton}>‹ Back</Text>
            </TouchableOpacity>
            <Text
              style={[
                styles.title,
                {color: isDarkMode ? Colors.white : Colors.black},
              ]}>
              {isEditing ? 'Edit' : 'Create'} Segment
            </Text>
            <View style={{width: 60}} />
          </View>

          <Text
            numberOfLines={2}
            style={[
              styles.subtitle,
              {color: isDarkMode ? Colors.light : Colors.dark},
            ]}>
            {itemName}
          </Text>

          <View style={styles.section}>
            <Text
              style={[
                styles.label,
                {color: isDarkMode ? Colors.light : Colors.dark},
              ]}>
              Segment Type {isEditing && '(cannot be changed)'}
            </Text>
            <View style={styles.typeButtonContainer}>
              {segmentTypes.map(renderSegmentTypeButton)}
            </View>
          </View>

          <View style={styles.section}>
            <Text
              style={[
                styles.label,
                {color: isDarkMode ? Colors.light : Colors.dark},
              ]}>
              Start Time (MM:SS or HH:MM:SS)
            </Text>
            <TextInput
              style={[
                styles.input,
                {
                  color: isDarkMode ? Colors.white : Colors.black,
                  borderColor: isDarkMode ? Colors.light : Colors.dark,
                },
              ]}
              placeholder="00:00"
              placeholderTextColor={isDarkMode ? Colors.light : Colors.dark}
              value={startTime}
              onChangeText={setStartTime}
              keyboardType="numbers-and-punctuation"
            />
            <Text
              style={[
                styles.hint,
                {color: isDarkMode ? Colors.light : Colors.dark},
              ]}>
              {secondsToTicks(parseTimeToSeconds(startTime))} ticks
            </Text>
          </View>

          <View style={styles.section}>
            <Text
              style={[
                styles.label,
                {color: isDarkMode ? Colors.light : Colors.dark},
              ]}>
              End Time (MM:SS or HH:MM:SS)
            </Text>
            <TextInput
              style={[
                styles.input,
                {
                  color: isDarkMode ? Colors.white : Colors.black,
                  borderColor: isDarkMode ? Colors.light : Colors.dark,
                },
              ]}
              placeholder="00:00"
              placeholderTextColor={isDarkMode ? Colors.light : Colors.dark}
              value={endTime}
              onChangeText={setEndTime}
              keyboardType="numbers-and-punctuation"
            />
            <Text
              style={[
                styles.hint,
                {color: isDarkMode ? Colors.light : Colors.dark},
              ]}>
              {secondsToTicks(parseTimeToSeconds(endTime))} ticks
            </Text>
          </View>

          <View style={styles.timelineSection}>
            <Text
              style={[
                styles.label,
                {color: isDarkMode ? Colors.light : Colors.dark},
              ]}>
              Timeline Visualization
            </Text>
            <View
              style={[
                styles.timeline,
                {backgroundColor: isDarkMode ? Colors.dark : Colors.white},
              ]}>
              <View
                style={[
                  styles.timelineSegment,
                  {
                    backgroundColor:
                      selectedType === SegmentType.Intro
                        ? Colors.primary
                        : selectedType === SegmentType.Outro
                        ? Colors.secondary
                        : selectedType === SegmentType.Recap
                        ? Colors.warning
                        : selectedType === SegmentType.Preview
                        ? Colors.danger
                        : Colors.success,
                  },
                ]}>
                <Text style={styles.timelineText}>
                  {(() => {
                    const startSeconds = parseTimeToSeconds(startTime);
                    const endSeconds = parseTimeToSeconds(endTime);
                    const duration = endSeconds - startSeconds;
                    
                    // Handle invalid durations
                    if (isNaN(duration) || duration < 0) {
                      return 'Invalid';
                    }
                    
                    return formatTime(duration);
                  })()}
                </Text>
              </View>
            </View>
            <View style={styles.timelineLabels}>
              <Text
                style={[
                  styles.timelineLabel,
                  {color: isDarkMode ? Colors.light : Colors.dark},
                ]}>
                Start: {startTime}
              </Text>
              <Text
                style={[
                  styles.timelineLabel,
                  {color: isDarkMode ? Colors.light : Colors.dark},
                ]}>
                End: {endTime}
              </Text>
            </View>
          </View>

          <TouchableOpacity
            style={[
              styles.button,
              styles.saveButton,
              saving && styles.buttonDisabled,
            ]}
            onPress={handleSave}
            disabled={saving}>
            <Text style={styles.buttonText}>
              {saving ? 'Saving...' : isEditing ? 'Update Segment' : 'Create Segment'}
            </Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
    minHeight: '100%',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  backButton: {
    fontSize: 24,
    color: Colors.primary,
    fontWeight: 'bold',
    width: 60,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    flex: 1,
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 16,
    marginBottom: 24,
    textAlign: 'center',
  },
  section: {
    marginBottom: 24,
  },
  label: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 8,
  },
  typeButtonContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  typeButton: {
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 8,
    borderWidth: 1,
  },
  typeButtonSelected: {
    backgroundColor: Colors.primary,
    borderColor: Colors.primary,
  },
  typeButtonText: {
    fontSize: 14,
    fontWeight: '600',
  },
  typeButtonTextSelected: {
    color: Colors.white,
  },
  input: {
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
  },
  hint: {
    fontSize: 12,
    marginTop: 4,
  },
  timelineSection: {
    marginBottom: 24,
  },
  timeline: {
    height: 60,
    borderRadius: 8,
    padding: 8,
    justifyContent: 'center',
    marginBottom: 8,
  },
  timelineSegment: {
    height: '100%',
    borderRadius: 6,
    justifyContent: 'center',
    alignItems: 'center',
  },
  timelineText: {
    color: Colors.white,
    fontSize: 16,
    fontWeight: 'bold',
  },
  timelineLabels: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  timelineLabel: {
    fontSize: 12,
  },
  button: {
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
    marginVertical: 8,
  },
  saveButton: {
    backgroundColor: Colors.success,
  },
  buttonDisabled: {
    opacity: 0.5,
  },
  buttonText: {
    color: Colors.white,
    fontSize: 16,
    fontWeight: '600',
  },
});

export default SegmentEditorScreen;
