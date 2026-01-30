/**
 * Jellyfin API Service - React Native Implementation
 * Replaces the native Kotlin JellyfinApiService
 */

import axios, {AxiosInstance} from 'axios';
import EncryptedStorage from 'react-native-encrypted-storage';

export enum SegmentType {
  Intro = 'Intro',
  Outro = 'Outro',
  Recap = 'Recap',
  Preview = 'Preview',
  Credits = 'Credits',
}

export interface Segment {
  ItemId: string;
  Type: SegmentType;
  StartTicks: number;
  EndTicks: number;
}

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
}

class JellyfinApiService {
  private static instance: JellyfinApiService;
  private axiosInstance: AxiosInstance | null = null;
  private serverUrl: string = '';
  private apiKey: string = '';

  private constructor() {}

  static getInstance(): JellyfinApiService {
    if (!JellyfinApiService.instance) {
      JellyfinApiService.instance = new JellyfinApiService();
    }
    return JellyfinApiService.instance;
  }

  /**
   * Initialize the API client with server URL and API key
   */
  async initialize() {
    try {
      this.serverUrl = (await EncryptedStorage.getItem('serverUrl')) || '';
      this.apiKey = (await EncryptedStorage.getItem('apiKey')) || '';

      if (this.serverUrl && this.apiKey) {
        this.axiosInstance = axios.create({
          baseURL: this.serverUrl,
          timeout: 30000,
          headers: {
            'X-Emby-Token': this.apiKey,
            'Content-Type': 'application/json',
          },
        });
      }
    } catch (error) {
      console.error('Failed to initialize API service:', error);
    }
  }

  /**
   * Save credentials to encrypted storage
   */
  async saveCredentials(serverUrl: string, apiKey: string): Promise<void> {
    await EncryptedStorage.setItem('serverUrl', serverUrl);
    await EncryptedStorage.setItem('apiKey', apiKey);
    this.serverUrl = serverUrl;
    this.apiKey = apiKey;
    
    // Reinitialize axios instance
    this.axiosInstance = axios.create({
      baseURL: this.serverUrl,
      timeout: 30000,
      headers: {
        'X-Emby-Token': this.apiKey,
        'Content-Type': 'application/json',
      },
    });
  }

  /**
   * Get saved server URL
   */
  async getServerUrl(): Promise<string> {
    return (await EncryptedStorage.getItem('serverUrl')) || '';
  }

  /**
   * Get saved API key
   */
  async getApiKey(): Promise<string> {
    return (await EncryptedStorage.getItem('apiKey')) || '';
  }

  /**
   * Test connection to Jellyfin server
   */
  async testConnection(): Promise<ApiResponse<any>> {
    try {
      if (!this.axiosInstance) {
        return {
          success: false,
          error: 'API not initialized. Please save credentials first.',
        };
      }

      const response = await this.axiosInstance.get('/System/Info');
      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        error: error.message || 'Connection failed',
      };
    }
  }

  /**
   * Get all segments for a media item
   */
  async getSegments(itemId: string): Promise<ApiResponse<Segment[]>> {
    try {
      if (!this.axiosInstance) {
        return {
          success: false,
          error: 'API not initialized',
        };
      }

      const response = await this.axiosInstance.get(
        `/Items/${itemId}/Segments`,
      );
      return {
        success: true,
        data: response.data || [],
      };
    } catch (error: any) {
      return {
        success: false,
        error: error.message || 'Failed to fetch segments',
      };
    }
  }

  /**
   * Create a new segment
   */
  async createSegment(segment: Segment): Promise<ApiResponse<Segment>> {
    try {
      if (!this.axiosInstance) {
        return {
          success: false,
          error: 'API not initialized',
        };
      }

      const response = await this.axiosInstance.post('/Segments', segment);
      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        error: error.message || 'Failed to create segment',
      };
    }
  }

  /**
   * Update an existing segment
   */
  async updateSegment(
    itemId: string,
    type: SegmentType,
    segment: Segment,
  ): Promise<ApiResponse<Segment>> {
    try {
      if (!this.axiosInstance) {
        return {
          success: false,
          error: 'API not initialized',
        };
      }

      const response = await this.axiosInstance.put(
        `/Items/${itemId}/Segments/${type}`,
        segment,
      );
      return {
        success: true,
        data: response.data,
      };
    } catch (error: any) {
      return {
        success: false,
        error: error.message || 'Failed to update segment',
      };
    }
  }

  /**
   * Delete a segment
   */
  async deleteSegment(
    itemId: string,
    type: SegmentType,
  ): Promise<ApiResponse<void>> {
    try {
      if (!this.axiosInstance) {
        return {
          success: false,
          error: 'API not initialized',
        };
      }

      await this.axiosInstance.delete(`/Items/${itemId}/Segments/${type}`);
      return {
        success: true,
      };
    } catch (error: any) {
      return {
        success: false,
        error: error.message || 'Failed to delete segment',
      };
    }
  }

  /**
   * Convert ticks to seconds (Jellyfin uses 10,000,000 ticks per second)
   */
  static ticksToSeconds(ticks: number): number {
    return ticks / 10000000;
  }

  /**
   * Convert seconds to ticks
   */
  static secondsToTicks(seconds: number): number {
    return Math.round(seconds * 10000000);
  }

  /**
   * Format ticks to timestamp string (HH:MM:SS or MM:SS)
   */
  static formatTicks(ticks: number): string {
    const totalSeconds = Math.floor(ticks / 10000000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    const pad = (num: number) => num.toString().padStart(2, '0');

    if (hours > 0) {
      return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
    }
    return `${pad(minutes)}:${pad(seconds)}`;
  }
}

export default JellyfinApiService.getInstance();
