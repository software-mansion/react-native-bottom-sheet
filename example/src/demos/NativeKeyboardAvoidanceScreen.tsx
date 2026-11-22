import { useRef, useState } from 'react';
import {
  Button,
  Keyboard,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { ModalBottomSheet } from '@swmansion/react-native-bottom-sheet';

import {
  DemoScreen,
  MODAL_SCRIM_COLOR,
  SheetBackground,
  SheetHeader,
  useSheetBottomPadding,
} from '../demoShared';

const SHORT_FIELDS = ['First name', 'Last name'];
const TALL_FIELDS = [
  'First name',
  'Last name',
  'Email',
  'Phone',
  'Address',
  'City',
  'Postcode',
  'Country',
  'Company',
  'Job title',
  'Website',
  'Notes',
];
// Visible space between the last content and the keyboard.
const KEYBOARD_GAP = 16;

export const NativeKeyboardAvoidanceScreen = () => {
  const [index, setIndex] = useState(0);
  const [fields, setFields] = useState(SHORT_FIELDS);
  const bottomPadding = useSheetBottomPadding();
  const inputs = useRef<(TextInput | null)[]>([]);

  const open = (next: string[]) => {
    setFields(next);
    setIndex(1);
  };
  const close = () => {
    Keyboard.dismiss();
    setIndex(0);
  };

  return (
    <DemoScreen
      title="Native keyboard avoidance"
      sheet={
        <ModalBottomSheet
          detents={[0, 'content']}
          index={index}
          onIndexChange={setIndex}
          keyboardAvoidance
          // The content's safe-area bottom padding tucks behind the keyboard,
          // leaving KEYBOARD_GAP visible above it.
          keyboardOffset={-(bottomPadding - KEYBOARD_GAP)}
          scrimColor={MODAL_SCRIM_COLOR}
          surface={<SheetBackground style={StyleSheet.absoluteFill} />}
        >
          <SheetHeader title="Your details" onClose={close} />
          <ScrollView
            keyboardShouldPersistTaps="handled"
            style={styles.scroll}
            contentContainerStyle={[
              styles.form,
              { paddingBottom: bottomPadding },
            ]}
          >
            {fields.map((label, i) => (
              <TextInput
                key={label}
                ref={(input) => {
                  inputs.current[i] = input;
                }}
                placeholder={label}
                returnKeyType={i < fields.length - 1 ? 'next' : 'done'}
                submitBehavior={
                  i < fields.length - 1 ? 'submit' : 'blurAndSubmit'
                }
                onSubmitEditing={() => inputs.current[i + 1]?.focus()}
                style={styles.input}
              />
            ))}
            <Button title="Save" onPress={close} />
          </ScrollView>
        </ModalBottomSheet>
      }
    >
      <Button title="Open short form" onPress={() => open(SHORT_FIELDS)} />
      <Button title="Open tall form" onPress={() => open(TALL_FIELDS)} />
      <View style={styles.statePanel}>
        <Text style={styles.stateTitle}>Current setup</Text>
        <Text>detents: [0, 'content']</Text>
        <Text>keyboardAvoidance: true</Text>
        <Text>keyboardOffset: -(bottom padding - {KEYBOARD_GAP})</Text>
      </View>
    </DemoScreen>
  );
};

const styles = StyleSheet.create({
  scroll: {
    flexGrow: 0,
  },
  form: {
    paddingTop: 16,
    paddingHorizontal: 20,
    gap: 10,
  },
  input: {
    height: 48,
    borderWidth: 1,
    borderColor: '#d7dce2',
    borderRadius: 12,
    paddingHorizontal: 14,
    fontSize: 16,
    backgroundColor: '#f8fafc',
  },
  statePanel: {
    padding: 16,
    borderRadius: 16,
    backgroundColor: '#f3f3f3',
    gap: 6,
  },
  stateTitle: {
    fontWeight: '600',
  },
});
