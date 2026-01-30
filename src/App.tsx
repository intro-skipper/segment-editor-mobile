/**
 * Segment Editor React Native App
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

import {Colors} from './styles/Colors';
import JellyfinApiService from './services/JellyfinApiService';

function App(): React.JSX.Element {
  const isDarkMode = useColorScheme() === 'dark';
  const [serverUrl, setServerUrl] = useState('');
  const [apiKey, setApiKey] = useState('');
  const [connected, setConnected] = useState(false);

  const backgroundStyle = {
    backgroundColor: isDarkMode ? Colors.darker : Colors.lighter,
    flex: 1,
  };

  useEffect(() => {
    // Load saved credentials
    loadCredentials();
  }, []);

  const loadCredentials = async () => {
    try {
      await JellyfinApiService.initialize();
      const savedServerUrl = await JellyfinApiService.getServerUrl();
      const savedApiKey = await JellyfinApiService.getApiKey();
      if (savedServerUrl) setServerUrl(savedServerUrl);
      if (savedApiKey) setApiKey(savedApiKey);
    } catch (error) {
      console.error('Failed to load credentials:', error);
    }
  };

  const handleSaveCredentials = async () => {
    try {
      await JellyfinApiService.saveCredentials(serverUrl, apiKey);
      Alert.alert('Success', 'Credentials saved successfully');
    } catch (error) {
      Alert.alert('Error', 'Failed to save credentials');
    }
  };

  const handleTestConnection = async () => {
    try {
      const result = await JellyfinApiService.testConnection();
      if (result.success) {
        setConnected(true);
        Alert.alert('Success', 'Connected to Jellyfin server!');
      } else {
        setConnected(false);
        Alert.alert('Error', result.error || 'Connection failed');
      }
    } catch (error) {
      setConnected(false);
      Alert.alert('Error', 'Connection test failed');
    }
  };

  return (
    <SafeAreaView style={backgroundStyle}>
      <StatusBar
        barStyle={isDarkMode ? 'light-content' : 'dark-content'}
        backgroundColor={backgroundStyle.backgroundColor}
      />
      <ScrollView
        contentInsetAdjustmentBehavior="automatic"
        style={backgroundStyle}>
        <View
          style={[
            styles.container,
            {
              backgroundColor: isDarkMode ? Colors.black : Colors.white,
            },
          ]}>
          <Text style={[styles.title, {color: isDarkMode ? Colors.white : Colors.black}]}>
            Jellyfin Segment Editor
          </Text>
          
          <View style={styles.section}>
            <Text style={[styles.label, {color: isDarkMode ? Colors.light : Colors.dark}]}>
              Server URL
            </Text>
            <TextInput
              style={[styles.input, {color: isDarkMode ? Colors.white : Colors.black, borderColor: isDarkMode ? Colors.light : Colors.dark}]}
              placeholder="https://jellyfin.example.com"
              placeholderTextColor={isDarkMode ? Colors.light : Colors.dark}
              value={serverUrl}
              onChangeText={setServerUrl}
              autoCapitalize="none"
              autoCorrect={false}
            />
          </View>

          <View style={styles.section}>
            <Text style={[styles.label, {color: isDarkMode ? Colors.light : Colors.dark}]}>
              API Key
            </Text>
            <TextInput
              style={[styles.input, {color: isDarkMode ? Colors.white : Colors.black, borderColor: isDarkMode ? Colors.light : Colors.dark}]}
              placeholder="Your Jellyfin API Key"
              placeholderTextColor={isDarkMode ? Colors.light : Colors.dark}
              value={apiKey}
              onChangeText={setApiKey}
              autoCapitalize="none"
              autoCorrect={false}
              secureTextEntry
            />
          </View>

          <TouchableOpacity
            style={[styles.button, styles.primaryButton]}
            onPress={handleSaveCredentials}>
            <Text style={styles.buttonText}>Save Credentials</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.button, connected ? styles.successButton : styles.secondaryButton]}
            onPress={handleTestConnection}>
            <Text style={styles.buttonText}>
              {connected ? '✓ Connected' : 'Test Connection'}
            </Text>
          </TouchableOpacity>

          {connected && (
            <View style={styles.connectedContainer}>
              <Text style={[styles.connectedText, {color: isDarkMode ? Colors.white : Colors.black}]}>
                Connected to Jellyfin server
              </Text>
              <Text style={[styles.infoText, {color: isDarkMode ? Colors.light : Colors.dark}]}>
                You can now manage media segments
              </Text>
            </View>
          )}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 24,
    minHeight: '100%',
  },
  title: {
    fontSize: 32,
    fontWeight: 'bold',
    marginBottom: 32,
    textAlign: 'center',
  },
  section: {
    marginBottom: 20,
  },
  label: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 8,
  },
  input: {
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
  },
  button: {
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
    marginVertical: 8,
  },
  primaryButton: {
    backgroundColor: '#007AFF',
  },
  secondaryButton: {
    backgroundColor: '#5856D6',
  },
  successButton: {
    backgroundColor: '#34C759',
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  connectedContainer: {
    marginTop: 24,
    padding: 16,
    backgroundColor: '#E8F5E9',
    borderRadius: 8,
  },
  connectedText: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 4,
  },
  infoText: {
    fontSize: 14,
  },
});

export default App;
