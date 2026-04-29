package com.nexuslabs.vector.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.logging.*;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.concurrent.*;

public class VectorApp extends JFrame {

    private static final Logger logger = Logger.getLogger("VectorApp");
    private JTextArea chat;
    private JTextField input;
    private JButton send;
    private volatile boolean loading = false;
    private Color bgColor = new Color(30, 30, 40);
    private Color inputBgColor = new Color(50, 50, 70);
    private Color textColor = Color.WHITE;
    private Color userColor = new Color(100, 180, 255);
    private Color botColor = Color.WHITE;
    private Color errorColor = new Color(255, 100, 100);

    static {
        try {
            FileHandler fh = new FileHandler("vector-app.log", true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
            logger.setLevel(Level.ALL);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public VectorApp() {
        super("V.E.C.T.O.R - AI Assistant");
        logger.info("Starting V.E.C.T.O.R Desktop App");
        
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(550, 650);
        setLocationRelativeTo(null);
        
        // Force dark mode before creating components
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
            UIManager.put("Panel.background", bgColor);
            UIManager.put("TextField.background", inputBgColor);
            UIManager.put("TextField.foreground", textColor);
            UIManager.put("TextArea.background", bgColor);
            UIManager.put("TextArea.foreground", textColor);
            UIManager.put("Button.background", new Color(50, 100, 200));
            UIManager.put("Button.foreground", Color.WHITE);
        } catch (Exception e) {
            logger.warning("Could not set look and feel: " + e.getMessage());
        }
        
        setBackground(bgColor);
        getContentPane().setBackground(bgColor);
        
        logger.info("Creating UI components");
        
        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(30, 30, 50));
        header.setPreferredSize(new Dimension(0, 50));
        
        JLabel title = new JLabel("V.E.C.T.O.R");
        title.setFont(new Font("Arial", Font.BOLD, 20));
        title.setForeground(new Color(0, 200, 255));
        
        JLabel status = new JLabel("Online");
        status.setFont(new Font("Arial", Font.PLAIN, 12));
        status.setForeground(new Color(100, 255, 100));
        
        header.add(title, BorderLayout.WEST);
        header.add(status, BorderLayout.EAST);
        header.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        
        // Chat area
        chat = new JTextArea();
        chat.setEditable(false);
        chat.setLineWrap(true);
        chat.setWrapStyleWord(true);
        chat.setFont(new Font("Arial", Font.PLAIN, 14));
        chat.setBackground(bgColor);
        chat.setForeground(textColor);
        chat.setCaretColor(new Color(0, 200, 255));
        chat.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        
        // Make sure background shows
        chat.setOpaque(true);
        JPanel chatPanel = new JPanel(new GridLayout());
        chatPanel.setBackground(bgColor);
        chatPanel.add(chat);
        
        // Input area
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.setBackground(new Color(30, 30, 50));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        input = new JTextField();
        input.setFont(new Font("Arial", Font.PLAIN, 14));
        input.setBackground(inputBgColor);
        input.setForeground(textColor);
        input.setCaretColor(new Color(0, 200, 255));
        input.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        send = new JButton("Send");
        send.setFont(new Font("Arial", Font.BOLD, 14));
        send.setBackground(new Color(50, 100, 200));
        send.setForeground(Color.WHITE);
        send.setFocusPainted(false);
        send.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        
        inputPanel.add(input, BorderLayout.CENTER);
        inputPanel.add(send, BorderLayout.EAST);
        
        // Add components
        add(header, BorderLayout.NORTH);
        add(chatPanel, BorderLayout.CENTER);
        add(inputPanel, BorderLayout.SOUTH);
        
        // Event handlers
        send.addActionListener(e -> sendMessage());
        input.addActionListener(e -> sendMessage());
        
        setVisible(true);
        
        appendChat("bot", "Hello! I'm V.E.C.T.O.R.\nHow can I help?");
        logger.info("App started successfully");
    }

    private void sendMessage() {
        String question = input.getText().trim();
        if (question.isEmpty() || loading) return;
        
        logger.info("Sending: " + question);
        appendChat("user", question);
        input.setText("");
        setLoading(true);
        
        new Thread(() -> {
            try {
                String response = askAPI(question);
                logger.info("Got response");
                SwingUtilities.invokeLater(() -> {
                    appendChat("bot", response);
                    setLoading(false);
                });
            } catch (Exception e) {
                logger.severe("Error: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    appendChat("error", "Error: " + e.getMessage());
                    setLoading(false);
                });
            }
        }).start();
    }

    private String askAPI(String question) throws Exception {
        URL url = new URL("http://localhost:8080/api/ask");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        String json = "{\"question\":\"" + escapeJson(question) + "\"}";
        conn.getOutputStream().write(json.getBytes());
        
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new Exception("Server error: " + code);
        }
        
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        
        return parseJson(response.toString(), "answer");
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String parseJson(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) return "No response";
        idx = json.indexOf(":", idx) + 1;
        while (idx < json.length() && json.charAt(idx) <= ' ') idx++;
        
        int end = json.indexOf(",", idx);
        int end2 = json.indexOf("}", idx);
        if (end < 0) end = Integer.MAX_VALUE;
        if (end2 < 0) end2 = Integer.MAX_VALUE;
        end = Math.min(end, end2);
        
        return json.substring(idx, end).replace("\"", "").trim();
    }

    private void appendChat(String type, String text) {
        if (type.equals("user")) {
            chat.append("\nYou: ");
            chat.setForeground(userColor);
            chat.append(text + "\n");
            chat.setForeground(botColor);
            chat.append("\n");
        } else if (type.equals("error")) {
            chat.append("\nError: ");
            chat.setForeground(errorColor);
            chat.append(text + "\n");
            chat.setForeground(botColor);
            chat.append("\n");
        } else {
            chat.append("\nV.E.C.T.O.R: ");
            chat.setForeground(botColor);
            chat.append(text + "\n\n");
        }
        chat.setCaretPosition(chat.getText().length());
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        send.setEnabled(!loading);
        input.setEnabled(!loading);
        send.setText(loading ? "..." : "Send");
    }

    public static void main(String[] args) {
        logger.info("Main started");
        SwingUtilities.invokeLater(() -> new VectorApp());
    }
}