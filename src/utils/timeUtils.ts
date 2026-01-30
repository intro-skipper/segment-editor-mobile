/**
 * Time and formatting utility functions
 */

import {Clipboard, Alert} from 'react-native';

/**
 * Convert ticks to seconds (Jellyfin uses 10,000,000 ticks per second)
 */
export function ticksToSeconds(ticks: number): number {
  return ticks / 10000000;
}

/**
 * Convert seconds to ticks
 */
export function secondsToTicks(seconds: number): number {
  return Math.round(seconds * 10000000);
}

/**
 * Format seconds to timestamp string (HH:MM:SS or MM:SS)
 */
export function formatTime(seconds: number): string {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const secs = Math.floor(seconds % 60);

  const pad = (num: number) => num.toString().padStart(2, '0');

  if (hours > 0) {
    return `${pad(hours)}:${pad(minutes)}:${pad(secs)}`;
  }
  return `${pad(minutes)}:${pad(secs)}`;
}

/**
 * Format ticks to timestamp string (HH:MM:SS or MM:SS)
 */
export function formatTicks(ticks: number): string {
  return formatTime(ticksToSeconds(ticks));
}

/**
 * Parse timestamp string (HH:MM:SS or MM:SS) to seconds
 */
export function parseTimeToSeconds(timeStr: string): number {
  const parts = timeStr.split(':').map(p => parseInt(p, 10));
  
  // Validate all parts are valid numbers
  if (parts.some(n => isNaN(n) || n < 0)) {
    return 0;
  }
  
  if (parts.length === 2) {
    // MM:SS
    return parts[0] * 60 + parts[1];
  } else if (parts.length === 3) {
    // HH:MM:SS
    return parts[0] * 3600 + parts[1] * 60 + parts[2];
  }
  
  return 0;
}

/**
 * Copy text to clipboard with user feedback
 */
export async function copyToClipboard(text: string, successMessage: string = 'Copied to clipboard'): Promise<void> {
  try {
    await Clipboard.setString(text);
    Alert.alert('Success', successMessage);
  } catch (error) {
    Alert.alert('Error', 'Failed to copy to clipboard');
  }
}
