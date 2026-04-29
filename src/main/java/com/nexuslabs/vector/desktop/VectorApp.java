package com.nexuslabs.vector.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public VectorApp() {
        super("V.E.C.T.O.R - AI Assistant");
        logger.info("Starting V.E.C.T.O.R Desktop App");
        
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(600, 700);
        setLocationRelativeTo(null);
        
        initUI();
        setVisible(true);
        
        appendChat("bot", "Hello! I'm V.E.C.T.O.R. How can I help?");
        logger.info("App started successfully");
    }

    private void initUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(15, 15, 25));
        
        chat = new JTextArea();
        chat.setEditable(false);
        chat.setLineWrap(true);
        chat.setWrapStyleWord(true);
        chat.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        chat.setBackground(new Color(15, 15, 25));
        chat.setForeground(new Color(220, 220, 220));
        chat.setCaretColor(new Color(0, 212, 255));
        chat.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        JScrollPane scroll = new JScrollPane(chat);
        scroll.setBackground(new Color(15, 15, 25));
        scroll.setBorder(null);
        
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(30, 30, 50));
        header.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));
        
        JLabel title = new JLabel("V.E.C.T.O.R");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(new Color(0, 212, 255));
        
        JLabel status = new JLabel("●");
        status.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        status.setForeground(new Color(0, 255, 100));
        
        header.add(title, BorderLayout.WEST);
        header.add(status, BorderLayout.EAST);
        
        input = new JTextField();
        input.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        input.setBackground(new Color(30, 30, 50));
        input.setForeground(Color.WHITE);
        input.setCaretColor(Color.WHITE);
        input.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));
        input.addActionListener(e -> sendMessage());
        
        send = new JButton("Send");
        send.setFont(new Font("Segoe UI", Font.BOLD, 14));
        send.setBackground(new Color(37, 99, 235));
        send.setForeground(Color.WHITE);
        send.setFocusPainted(false);
        send.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        send.addActionListener(e -> sendMessage());
        
        JPanel bottom = new JPanel(new BorderLayout(5, 0));
        bottom.setBackground(new Color(30, 30, 50));
        bottom.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(send, BorderLayout.EAST);
        
        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

private void sendMessage() {
        String question = input.getText().trim();
        if (question.isEmpty() || loading) return;
        
        logger.info("Sending question: " + question);
        appendChat("user", question);
        input.setText("");
        setLoading(true);
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                String response = askAPI(question);
                logger.info("Got response: " + response.substring(0, Math.min(100, response.length())) + "...");
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
            executor.shutdown();
        });
    }

    private String askAPI(String question) throws Exception {
        logger.info("Connecting to API...");
        URL url = new URL("http://localhost:8080/api/ask");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        String json = "{\"question\":\"" + escapeJson(question) + "\"}";
        conn.getOutputStream().write(json.getBytes());
        
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new Exception("Server returned " + code);
        }
        
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        
        String ans = parseJson(response.toString(), "answer");
        return ans != null ? ans : "No response";
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String parseJson(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) return null;
        idx = json.indexOf(":", idx) + 1;
        while (idx < json.length() && json.charAt(idx) <= ' ') idx++;
        
        if (idx >= json.length()) return null;
        
        if (json.charAt(idx) != '"') {
            int end = idx;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(idx, end).replaceAll("[\\\\]\"", "\"").trim();
        }
        
        int end = ++idx;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\') break;
            end++;
        }
        
        return json.substring(idx, end).replace("\\\\", "\\").replace("\\\"", "\"");
    }

    private void appendChat(String type, String text) {
        Color color = type.equals("user") ? new Color(100, 150, 255) :
                    type.equals("error") ? new Color(255, 100, 100) :
                    new Color(220, 220, 220);
        
        String prefix = type.equals("user") ? "\nYou: " :
                     type.equals("error") ? "\nError: " : "\nV.E.C.T.O.R: ";
        
        chat.setCaretPosition(chat.getDocument().getLength());
        chat.setForeground(color);
        chat.append(prefix + text + "\n");
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        send.setEnabled(!loading);
        input.setEnabled(!loading);
        send.setText(loading ? "..." : "Send");
    }

    public static void main(String[] args) {
        logger.info("Main method started");
        
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                logger.info("Look and feel set");
                new VectorApp();
                logger.info("VectorApp created");
            } catch (Exception e) {
                logger.severe("Fatal error: " + e.getMessage());
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, 
                    "Error starting app: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}