import React from 'react';
import {
  View,
  Text,
  StyleSheet,
} from 'react-native';

const PlayerScreen: React.FC = () => {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>Media Player</Text>
      <Text style={styles.description}>
        Player screen for managing segments and timestamps
      </Text>
      <View style={styles.placeholderBox}>
        <Text style={styles.placeholderText}>Video Player Placeholder</Text>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    backgroundColor: '#f5f5f5',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 10,
    color: '#1f2937',
  },
  description: {
    fontSize: 16,
    marginBottom: 20,
    color: '#4b5563',
  },
  placeholderBox: {
    flex: 1,
    backgroundColor: '#000',
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: 8,
  },
  placeholderText: {
    color: '#fff',
    fontSize: 18,
  },
});

export default PlayerScreen;
