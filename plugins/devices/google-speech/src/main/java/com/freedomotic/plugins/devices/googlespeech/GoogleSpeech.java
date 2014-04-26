/**
 *
 * Copyright (c) 2009-2013 Freedomotic team http://freedomotic.com
 *
 * This file is part of Freedomotic
 *
 * This Program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2, or (at your option) any later version.
 *
 * This Program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Freedomotic; see the file COPYING. If not, see
 * <http://www.gnu.org/licenses/>.
 */
package com.freedomotic.plugins.devices.googlespeech;

import com.darkprograms.speech.microphone.Microphone;
import com.darkprograms.speech.microphone.MicrophoneAnalyzer;
import com.darkprograms.speech.recognizer.GoogleResponse;
import com.darkprograms.speech.recognizer.Recognizer;
import com.darkprograms.speech.synthesiser.Synthesiser;
import com.darkprograms.speech.util.AePlayWave;
import com.freedomotic.api.EventTemplate;
import com.freedomotic.api.Protocol;
import com.freedomotic.app.Freedomotic;
import com.freedomotic.exceptions.UnableToExecuteException;
import com.freedomotic.reactions.Command;
import com.freedomotic.util.Info;
import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.swing.UIManager;
import javazoom.jl.player.Player;

public class GoogleSpeech
        extends Protocol {

    private static final Logger LOG = Logger.getLogger(GoogleSpeech.class.getName());
    private int RECORD_TIME = configuration.getIntProperty("record-time", 3000);
    private int THRESOLD = configuration.getIntProperty("thresold", 10);
    private String PASSIVE_LISTENING = configuration.getStringProperty("passive-listening", "false");
    final int POLLING_WAIT;
    public String LANGUAGE_CODE = configuration.getStringProperty("language-code", "en-US");
    public AePlayWave aePlayWave;
    public Microphone mic = new Microphone(AudioFileFormat.Type.WAVE);
    private static final MicrophoneAnalyzer micAnalyzer = new MicrophoneAnalyzer(AudioFileFormat.Type.WAVE);
    File file = null;
    GoogleSpeechGUI pluginGUI = null;

    public GoogleSpeech() {
        super("Google Speech", "/google-speech/google-speech-manifest.xml");
        POLLING_WAIT = configuration.getIntProperty("time-between-reads", 2000);
        setPollingWait(POLLING_WAIT);
    }

    @Override
    protected void onShowGui() {

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            pluginGUI.setVisible(true);
            pluginGUI.setLocationRelativeTo(null);
            bindGuiToPlugin(pluginGUI);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    protected void onHideGui() {
        setDescription("My GUI is now hidden");
    }

    @Override
    protected void onRun() {
    }

    @Override
    protected void onStart() {
        LOG.info("Google Speech plugin started");
        pluginGUI = new GoogleSpeechGUI(this);
        if (PASSIVE_LISTENING.equalsIgnoreCase("true")) {
            new PassiveListening().start();
        }

    }

    @Override
    protected void onStop() {
        LOG.info("Google Speech stopped ");
    }

    @Override
    protected void onCommand(Command c)
            throws IOException, UnableToExecuteException {
        String message = c.getProperty("say");
        if (c != null) {
            say(LANGUAGE_CODE, message);
        }

    }

    @Override
    protected boolean canExecute(Command c) {
        //don't mind this method for now
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected void onEvent(EventTemplate event) {
        //don't mind this method for now
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /*
     * Thread class for continuous microphone listening
     */
    public class PassiveListening extends Thread {

        @Override
        public void run() {
            try {
                environmentListening();
            } catch (IOException ex) {
                LOG.severe(ex.getLocalizedMessage());
            }
        }
    }

    void environmentListening() throws IOException {

        String fileName = Info.PATH_DATA_FOLDER + "recordedFile.wav";
        
        try {
            micAnalyzer.open();
            micAnalyzer.captureAudioToFile(fileName);//Rewrites a 10 second minimum audio clip over and over again.
            final int THRESHOLD = THRESOLD;
            int ambientVolume = micAnalyzer.getAudioVolume();
            int speakingVolume = -2;
            boolean speaking = false;
            for (int i = 0; i < 1 || speaking; i++) {
                int volume = micAnalyzer.getAudioVolume();
                System.out.println("Audio volume =" + volume + "  Environment volume =" + ambientVolume);
                if (volume > ambientVolume + THRESHOLD) {
                    speakingVolume = volume;
                    speaking = true;
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        LOG.severe(ex.getLocalizedMessage());
                    }
                    System.out.println("SPEAKING");
                }
                if (speaking && volume + THRESHOLD < speakingVolume) {
                    break;
                }
                try {
                    Thread.sleep(200);//Your refreshRate
                } catch (InterruptedException ex) {
                    LOG.severe(ex.getLocalizedMessage());
                }
            }
            micAnalyzer.close();
            if (!speaking) {
                environmentListening();
            }
            Recognizer rec = new Recognizer(LANGUAGE_CODE);
            GoogleResponse out = rec.getRecognizedDataForWave(fileName);
            System.out.println("Google response " + out.getResponse());
            environmentListening();
        } catch (LineUnavailableException ex) {
            LOG.severe(ex.getLocalizedMessage());
        }
    }

    public void say(String languageCode, String message) {
        try {
            new GoogleSpeech.Speaker(languageCode, message).start();
        } catch (Exception e) {
            LOG.severe(Freedomotic.getStackTraceInfo(e));
        }
    }

    protected class PlayState implements Runnable {

        @Override
        public void run() {
            while (aePlayWave.isAlive()) {
                try {
                    Thread.sleep(200);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    public class Speaker extends Thread {

        String languageCode;
        String message;

        public Speaker(String languageCode, String message) {
            this.languageCode = languageCode;
            this.message = message;
        }

        @Override
        public void run() {
            Synthesiser synthesiser = new Synthesiser(languageCode);
            try {
                pluginGUI.setSynthStatus("Playing...");
                InputStream is = synthesiser.getMP3Data(message);
                Player player = new Player(is);
                player.play();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            pluginGUI.setSynthStatus("Waiting...");
        }
    }

    /*
     * Converts audio input stream into mp3 file called google.mp3 @param
     * InputStream @return void
     */
    void InputStreamToMP3File(InputStream inputStream) {

        try {
            File f = new File(Info.PATH_DATA_FOLDER + "google.mp3");
            System.out.println(f.getAbsolutePath());
            OutputStream out = new FileOutputStream(f);
            byte buf[] = new byte[1024];
            int len;
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
        }
    }

    public void startRecognition() {
        mic = new Microphone(AudioFileFormat.Type.WAVE);
        File recordedFile = new File(Info.PATH_DATA_FOLDER+"recordGUI.wav");//Name your file whatever you want
        
        try {
            mic.captureAudioToFile(recordedFile);
        } catch (Exception ex) {//Microphone not available or some other error.
            LOG.severe("ERROR: Microphone is not available.");
            ex.printStackTrace();
            //TODO Add your error Handling Here
        }
        /*
         * User records the voice here. Microphone starts a separate thread so
         * do whatever you want in the mean time.
         */
        try {
            System.out.println("Recording...");
            pluginGUI.setStatus("Recording ...");
            Thread.sleep(RECORD_TIME);
        } catch (InterruptedException ex) {
            // TODO Auto-generated catch block
            ex.printStackTrace();
        }
        mic.close();//Ends recording and frees the resources
        System.out.println("Recording stopped.");
        new Thread(new RecognizeThread(LANGUAGE_CODE, recordedFile)).start();
        recordedFile.deleteOnExit(); // file deleted when jvm stops


    }

    
    protected class RecognizeThread implements Runnable {

        String languageCode = null;
        File file = null;

        public RecognizeThread(String languageCode, File file) {
            this.languageCode = languageCode;
            this.file = file;
        }

        @Override
        public void run() {
            Recognizer recognizer = new Recognizer(languageCode);//Specify your language here.
            pluginGUI.setStatus("Recognizing...");
            //Although auto-detect is available, it is recommended you select your region for added accuracy.
            try {
                GoogleResponse googleResponse = recognizer.getRecognizedDataForWave(file);
                System.out.println("Google Response: " + googleResponse.getResponse());
                if (googleResponse != null) {
                    pluginGUI.setResponse(googleResponse.getResponse());
                    pluginGUI.setConfidence(String.valueOf(Double.parseDouble(googleResponse.getConfidence()) * 100));
                    setDescription("You said: " + googleResponse.getResponse());
                }
            } catch (Exception ex) {
                // TODO Handle how to respond if Google cannot be contacted
                LOG.severe("ERROR: Google cannot be contacted");
                ex.printStackTrace();
            }
            pluginGUI.setStatus("Waiting...");
            pluginGUI.startRecognition.setEnabled(true);
        }
    }
}
