/**
 * Navigation type definitions for React Navigation
 */

import {SegmentType} from '../services/JellyfinApiService';

export type RootStackParamList = {
  Home: undefined;
  MediaLibrary: undefined;
  VideoPlayer: {
    itemId: string;
    itemName: string;
    videoUrl: string;
  };
  SegmentList: {
    itemId: string;
    itemName: string;
  };
  SegmentEditor: {
    itemId: string;
    itemName: string;
    segmentType?: SegmentType;
    startTicks?: number;
    endTicks?: number;
  };
};
