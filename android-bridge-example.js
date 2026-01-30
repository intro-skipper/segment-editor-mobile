/**
 * Example JavaScript Bridge Integration for Android WebView
 * 
 * This file demonstrates how to use the native Android JellyfinBridge
 * from the web application running in the WebView.
 * 
 * Include this in your web app to enable native functionality.
 */

// Check if running in Android WebView
const isAndroid = typeof window.JellyfinBridge !== 'undefined';

/**
 * Android Bridge Wrapper
 * Provides a clean API for interacting with native Android features
 */
class JellyfinAndroidBridge {
  constructor() {
    this.available = isAndroid;
    this.callbackId = 0;
    this.callbacks = {};
  }

  /**
   * Generate a unique callback ID
   */
  generateCallbackId() {
    return `callback_${++this.callbackId}`;
  }

  /**
   * Register a callback and return its ID
   */
  registerCallback(callback) {
    const id = this.generateCallbackId();
    this.callbacks[id] = callback;
    
    // Make callback available globally for Android to call
    window[id] = (result) => {
      try {
        const data = typeof result === 'string' ? JSON.parse(result) : result;
        callback(data);
      } catch (error) {
        callback({ success: false, error: error.message });
      } finally {
        // Cleanup
        delete window[id];
        delete this.callbacks[id];
      }
    };
    
    return id;
  }

  /**
   * Get saved server URL
   */
  getServerUrl() {
    if (!this.available) return null;
    return window.JellyfinBridge.getServerUrl();
  }

  /**
   * Get saved API key
   */
  getApiKey() {
    if (!this.available) return null;
    return window.JellyfinBridge.getApiKey();
  }

  /**
   * Save Jellyfin credentials
   */
  saveCredentials(serverUrl, apiKey) {
    if (!this.available) {
      return Promise.reject(new Error('Bridge not available'));
    }
    
    return new Promise((resolve) => {
      window.onCredentialsSaved = (result) => {
        const data = typeof result === 'string' ? JSON.parse(result) : result;
        delete window.onCredentialsSaved;
        resolve(data);
      };
      window.JellyfinBridge.saveCredentials(serverUrl, apiKey);
    });
  }

  /**
   * Test connection to Jellyfin server
   */
  testConnection() {
    if (!this.available) {
      return Promise.reject(new Error('Bridge not available'));
    }
    
    return new Promise((resolve, reject) => {
      const callbackId = this.registerCallback((result) => {
        if (result.success) {
          resolve(result.data);
        } else {
          reject(new Error(result.error));
        }
      });
      window.JellyfinBridge.testConnection(callbackId);
    });
  }

  /**
   * Get all segments for a media item
   */
  getSegments(itemId) {
    if (!this.available) {
      return Promise.reject(new Error('Bridge not available'));
    }
    
    return new Promise((resolve, reject) => {
      const callbackId = this.registerCallback((result) => {
        if (result.success) {
          resolve(result.data);
        } else {
          reject(new Error(result.error));
        }
      });
      window.JellyfinBridge.getSegments(itemId, callbackId);
    });
  }

  /**
   * Create a new segment
   * @param {Object} segment - Segment object with ItemId, Type, StartTicks, EndTicks
   */
  createSegment(segment) {
    if (!this.available) {
      return Promise.reject(new Error('Bridge not available'));
    }
    
    return new Promise((resolve, reject) => {
      const callbackId = this.registerCallback((result) => {
        if (result.success) {
          resolve(result.data);
        } else {
          reject(new Error(result.error));
        }
      });
      window.JellyfinBridge.createSegment(JSON.stringify(segment), callbackId);
    });
  }

  /**
   * Update an existing segment
   */
  updateSegment(itemId, segmentType, segment) {
    if (!this.available) {
      return Promise.reject(new Error('Bridge not available'));
    }
    
    return new Promise((resolve, reject) => {
      const callbackId = this.registerCallback((result) => {
        if (result.success) {
          resolve(result.data);
        } else {
          reject(new Error(result.error));
        }
      });
      window.JellyfinBridge.updateSegment(
        itemId,
        segmentType,
        JSON.stringify(segment),
        callbackId
      );
    });
  }

  /**
   * Delete a segment
   */
  deleteSegment(itemId, segmentType) {
    if (!this.available) {
      return Promise.reject(new Error('Bridge not available'));
    }
    
    return new Promise((resolve, reject) => {
      const callbackId = this.registerCallback((result) => {
        if (result.success) {
          resolve();
        } else {
          reject(new Error(result.error));
        }
      });
      window.JellyfinBridge.deleteSegment(itemId, segmentType, callbackId);
    });
  }

  /**
   * Copy text to system clipboard
   */
  copyToClipboard(text) {
    if (!this.available) {
      return Promise.reject(new Error('Bridge not available'));
    }
    
    return new Promise((resolve) => {
      window.onClipboardCopy = (result) => {
        const data = typeof result === 'string' ? JSON.parse(result) : result;
        delete window.onClipboardCopy;
        resolve(data);
      };
      window.JellyfinBridge.copyToClipboard(text);
    });
  }

  /**
   * Open native video player
   */
  openVideoPlayer(videoUrl, itemId) {
    if (!this.available) {
      return Promise.reject(new Error('Bridge not available'));
    }
    
    window.JellyfinBridge.openVideoPlayer(videoUrl, itemId);
    return Promise.resolve();
  }

  /**
   * Helper: Convert seconds to Jellyfin ticks
   */
  secondsToTicks(seconds) {
    return Math.floor(seconds * 10_000_000);
  }

  /**
   * Helper: Convert Jellyfin ticks to seconds
   */
  ticksToSeconds(ticks) {
    return ticks / 10_000_000;
  }
}

// Create singleton instance
const androidBridge = new JellyfinAndroidBridge();

// Export for use in modules
if (typeof module !== 'undefined' && module.exports) {
  module.exports = androidBridge;
}

// Usage Examples:
/*

// Initialize and check availability
if (androidBridge.available) {
  console.log('Running in Android WebView');
}

// Get saved credentials
const serverUrl = androidBridge.getServerUrl();
const apiKey = androidBridge.getApiKey();

// Save new credentials
androidBridge.saveCredentials('https://jellyfin.example.com', 'your-api-key')
  .then(() => console.log('Credentials saved'))
  .catch(err => console.error('Failed to save:', err));

// Test connection
androidBridge.testConnection()
  .then(info => console.log('Server info:', info))
  .catch(err => console.error('Connection failed:', err));

// Get segments
androidBridge.getSegments('item-id-123')
  .then(segments => console.log('Segments:', segments))
  .catch(err => console.error('Failed to get segments:', err));

// Create a new segment
const newSegment = {
  ItemId: 'item-id-123',
  Type: 'Intro',
  StartTicks: androidBridge.secondsToTicks(10),  // 10 seconds
  EndTicks: androidBridge.secondsToTicks(90)     // 90 seconds
};

androidBridge.createSegment(newSegment)
  .then(created => console.log('Created segment:', created))
  .catch(err => console.error('Failed to create:', err));

// Update segment
androidBridge.updateSegment('item-id-123', 'Intro', {
  ItemId: 'item-id-123',
  Type: 'Intro',
  StartTicks: androidBridge.secondsToTicks(12),
  EndTicks: androidBridge.secondsToTicks(95)
})
  .then(updated => console.log('Updated segment:', updated))
  .catch(err => console.error('Failed to update:', err));

// Delete segment
androidBridge.deleteSegment('item-id-123', 'Intro')
  .then(() => console.log('Deleted segment'))
  .catch(err => console.error('Failed to delete:', err));

// Copy timestamp to clipboard
androidBridge.copyToClipboard('00:01:23')
  .then(() => console.log('Copied to clipboard'))
  .catch(err => console.error('Failed to copy:', err));

// Open video player
androidBridge.openVideoPlayer(
  'https://jellyfin.example.com/Videos/item-id/stream?api_key=key',
  'item-id-123'
)
  .then(() => console.log('Player opened'))
  .catch(err => console.error('Failed to open player:', err));

// Use async/await syntax
async function loadAndEditSegment() {
  try {
    // Get existing segments
    const segments = await androidBridge.getSegments('item-id-123');
    
    // Find intro segment
    const intro = segments.find(s => s.Type === 'Intro');
    
    if (intro) {
      // Update it
      intro.EndTicks = androidBridge.secondsToTicks(100);
      await androidBridge.updateSegment('item-id-123', 'Intro', intro);
      console.log('Updated intro segment');
    } else {
      // Create new intro
      const newIntro = {
        ItemId: 'item-id-123',
        Type: 'Intro',
        StartTicks: androidBridge.secondsToTicks(0),
        EndTicks: androidBridge.secondsToTicks(90)
      };
      await androidBridge.createSegment(newIntro);
      console.log('Created intro segment');
    }
  } catch (error) {
    console.error('Error:', error);
  }
}

*/
