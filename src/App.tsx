/**
 * Segment Editor React Native App
 * Main app with navigation
 */

import React from 'react';
import {NavigationContainer} from '@react-navigation/native';
import {createNativeStackNavigator} from '@react-navigation/native-stack';

import {RootStackParamList} from './types/navigation';
import HomeScreen from './screens/HomeScreen';
import MediaLibraryScreen from './screens/MediaLibraryScreen';
import VideoPlayerScreen from './screens/VideoPlayerScreen';
import SegmentListScreen from './screens/SegmentListScreen';
import SegmentEditorScreen from './screens/SegmentEditorScreen';

const Stack = createNativeStackNavigator<RootStackParamList>();

function App(): React.JSX.Element {
  return (
    <NavigationContainer>
      <Stack.Navigator
        initialRouteName="Home"
        screenOptions={{
          headerShown: false,
        }}>
        <Stack.Screen name="Home" component={HomeScreen} />
        <Stack.Screen name="MediaLibrary" component={MediaLibraryScreen} />
        <Stack.Screen name="VideoPlayer" component={VideoPlayerScreen} />
        <Stack.Screen name="SegmentList" component={SegmentListScreen} />
        <Stack.Screen name="SegmentEditor" component={SegmentEditorScreen} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}

export default App;
