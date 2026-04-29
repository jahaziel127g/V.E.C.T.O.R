package com.nexuslabs.vector.desktop;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.logging.*;

public class VectorApp extends JFrame {

    private static final Logger logger = Logger.getLogger("VectorApp");
    private JTextArea chat;
    private JTextField input;
    private JButton send;
    private volatile boolean loading = false;

    static {
        try {
            FileHandler fh = new FileHandler("vector-app.log", true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
            logger.setLevel(Level.ALL);
        } catch (Exception e) {}
    }

    public VectorApp() {
        super("V.E.C.T.O.R");
        logger.info("APP STARTED");
        
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(500, 600);
        setLocationRelativeTo(null);
        
        Color bgColor = new Color(35, 35, 50);
        Color darkBg = new Color(50, 50, 75);
        
        // Main panel with color
        setLayout(new BorderLayout());
        getContentPane().setBackground(bgColor);
        
        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(darkBg);
        header.setPreferredSize(new Dimension(0, 50));
        
        JLabel title = new JLabel("V.E.C.T.O.R");
        title.setFont(new Font("Arial", Font.BOLD, 20));
        title.setForeground(new Color(0, 212, 255));
        title.setBackground(darkBg);
        title.setOpaque(true);
        
        JLabel status = new JLabel("Online");
        status.setFont(new Font("Arial", Font.PLAIN, 12));
        status.setForeground(new Color(100, 255, 100));
        status.setBackground(darkBg);
        status.setOpaque(true);
        
        header.add(title, BorderLayout.WEST);
        header.add(status, BorderLayout.EAST);
        
        // Chat - white text on dark background
        chat = new JTextArea();
        chat.setEditable(false);
        chat.setBackground(bgColor);
        chat.setForeground(Color.WHITE);
        chat.setFont(new Font("Arial", Font.PLAIN, 14));
        chat.setCaretColor(Color.WHITE);
        chat.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        
        // Scroll for chat
        JScrollPane chatScroll = new JScrollPane(chat);
        chatScroll.setBackground(bgColor);
        chatScroll.setBorder(null);
        
        // Input field
        input = new JTextField();
        input.setBackground(darkBg);
        input.setForeground(Color.WHITE);
        input.setCaretColor(Color.WHITE);
        input.setFont(new Font("Arial", Font.PLAIN, 14));
        input.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Send button - blue
        send = new JButton("Send");
        send.setFont(new Font("Arial", Font.BOLD, 14));
        send.setBackground(new Color(60, 120, 220));
        send.setForeground(Color.WHITE);
        send.setFocusPainted(false);
        
        // Bottom panel
        JPanel bottom = new JPanel(new BorderLayout(5, 0));
        bottom.setBackground(darkBg);
        bottom.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(send, BorderLayout.EAST);
        
        // Add to frame
        add(header, BorderLayout.NORTH);
        add(chatScroll, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        
        // Events
        send.addActionListener(e -> onSend());
        input.addActionListener(e -> onSend());
        
        setVisible(true);
        
        // Say hello
        appendChat("Hello! I'm V.E.C.T.O.R");
        appendChat("How can I help you today?");
        
        logger.info("APP READY - showing UI");
    }

    private void onSend() {
        String q = input.getText().trim();
        if (q.isEmpty() || loading) return;
        
        appendChat("You: " + q);
        input.setText("");
        loading = true;
        send.setEnabled(false);
        
        logger.info("Sending: " + q);
        
        new Thread(() -> {
            try {
                String response = callAPI(q);
                logger.info("Got: " + response.substring(0, 50));
                SwingUtilities.invokeLater(() -> {
                    appendChat("V.E.C.T.O.R: " + response);
                    loading = false;
                    send.setEnabled(true);
                });
            } catch (Exception e) {
                logger.severe("Error: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    appendChat("ERROR: " + e.getMessage());
                    loading = false;
                    send.setEnabled(true);
                });
            }
        }).start();
    }

    private String callAPI(String q) throws Exception {
        URL url = new URL("http://localhost:8080/api/ask");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        
        String json = "{\"question\":\"" + q.replace("\"", "\\\"") + "\"}";
        c.getOutputStream().write(json.getBytes());
        
        if (c.getResponseCode() != 200) {
            throw new Exception("Server: " + c.getResponseCode());
        }
        
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        
        String s = sb.toString();
        int start = s.indexOf("\"answer\"");
        if (start < 0) return "No response";
        start = s.indexOf(":", start) + 1;
        int end = s.indexOf(",", start);
        if (end < 0) end = s.indexOf("}", start);
        return s.substring(start, end).replace("\"", "").trim();
    }

    private void appendChat(String text) {
        chat.append(text + "\n\n");
        chat.setCaretPosition(chat.getText().length());
    }

    public static void main(String[] args) {
        logger.info("MAIN START");
        SwingUtilities.invokeLater(() -> new VectorApp());
    }
}