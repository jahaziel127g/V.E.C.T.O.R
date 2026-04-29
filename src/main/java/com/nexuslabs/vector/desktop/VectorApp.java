package com.nexuslabs.vector.desktop;

import javax.swing.*;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class VectorApp extends JFrame {

    private JTextField input;
    private JTextPane chat;
    private JButton send;
    private JButton clear;
    private DefaultStyledDocument doc;
    private boolean loading = false;

    public VectorApp() {
        super("V.E.C.T.O.R - AI Assistant");
        
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(600, 700);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(400, 500));
        
        initComponents();
        addComponents();
        
        setVisible(true);
        appendMessage("bot", "Hello! I'm V.E.C.T.O.R. How can I help?");
    }

    private void initComponents() {
        doc = new DefaultStyledDocument();
        
        chat = new JTextPane(doc);
        chat.setEditable(false);
        chat.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chat.setBackground(new Color(10, 10, 15));
        chat.setForeground(new Color(220, 220, 220));
        
        input = new JTextField();
        input.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        input.setBackground(new Color(26, 26, 46));
        input.setForeground(Color.WHITE);
        input.setCaretColor(Color.WHITE);
        input.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        send = new JButton("Send");
        send.setFont(new Font("Segoe UI", Font.BOLD, 14));
        send.setBackground(new Color(37, 99, 235));
        send.setForeground(Color.WHITE);
        send.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        send.setFocusPainted(false);
        send.addActionListener(e -> sendMessage());
        
        clear = new JButton("Clear");
        clear.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        clear.setBackground(new Color(60, 60, 80));
        clear.setForeground(Color.LIGHT_GRAY);
        clear.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        clear.setFocusPainted(false);
        clear.addActionListener(e -> clearChat());
        
        input.addActionListener(e -> sendMessage());
    }

    private void addComponents() {
        setLayout(new BorderLayout(0, 0));
        
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(26, 26, 46));
        header.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        JLabel title = new JLabel("V.E.C.T.O.R");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(new Color(0, 212, 255));
        
        JLabel status = new JLabel("● Online");
        status.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        status.setForeground(new Color(0, 255, 136));
        
        header.add(title, BorderLayout.WEST);
        header.add(status, BorderLayout.EAST);
        
        JScrollPane scroll = new JScrollPane(chat);
        scroll.setBorder(null);
        scroll.setBackground(new Color(10, 10, 15));
        
        JPanel bottom = new JPanel(new BorderLayout(5, 0));
        bottom.setBackground(new Color(26, 26, 46));
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
        
        appendMessage("user", question);
        input.setText("");
        setLoading(true);
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                String response = askAPI(question);
                SwingUtilities.invokeLater(() -> {
                    appendMessage("bot", response);
                    setLoading(false);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    appendMessage("error", "Error: " + e.getMessage());
                    setLoading(false);
                });
            }
            executor.shutdown();
        });
    }

    private String askAPI(String question) throws Exception {
        URL url = new URL("http://localhost:8080/api/ask");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        String json = "{\"question\":\"" + question.replace("\"", "\\\"") + "\"}";
        conn.getOutputStream().write(json.getBytes());
        
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        
        String jsonResponse = response.toString();
        
        String answer = extractJson(jsonResponse, "answer");
        if (answer == null) {
            answer = extractJson(jsonResponse, "error");
            if (answer == null) answer = "No response";
        }
        
        return answer;
    }

    private String extractJson(String json, String key) {
        int keyStart = json.indexOf("\"" + key + "\"");
        if (keyStart == -1) return null;
        keyStart = json.indexOf(":", keyStart) + 1;
        while (keyStart < json.length() && json.charAt(keyStart) <= ' ') keyStart++;
        
        if (keyStart >= json.length()) return null;
        
        char endChar = json.charAt(keyStart);
        if (endChar != '"') {
            int end = keyStart;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(keyStart, end).replaceAll("[\\\\]\"", "\"").trim();
        }
        
        int end = keyStart + 1;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\') break;
            end++;
        }
        
        return json.substring(keyStart + 1, end).replaceAll("\\\\\"", "\"");
    }

    private void appendMessage(String type, String text) {
        try {
            StyleConstants.setForeground(doc.getStyle("default"), 
                type.equals("user") ? new Color(37, 99, 235) :
                type.equals("error") ? new Color(255, 80, 80) :
                new Color(220, 220, 220));
            
            doc.insertString(doc.getLength(), 
                (type.equals("bot") ? "V.E.C.T.O.R: " : 
                type.equals("error") ? "Error: " : "You: ") + text + "\n\n", 
                doc.getStyle("default"));
            
            chat.setCaretPosition(doc.getLength());
        } catch (Exception e) {}
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        send.setEnabled(!loading);
        input.setEnabled(!loading);
        send.setText(loading ? "..." : "Send");
    }

    private void clearChat() {
        try {
            doc.remove(0, doc.getLength());
            appendMessage("bot", "Chat cleared. How can I help?");
        } catch (Exception e) {}
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VectorApp());
    }
}