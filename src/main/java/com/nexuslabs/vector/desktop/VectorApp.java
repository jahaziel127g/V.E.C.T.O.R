package com.nexuslabs.vector.desktop;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

public class VectorApp {

    private JFrame frame;
    private JTextArea chat;
    private JTextField input;
    private JButton send;
    private boolean loading = false;

    public VectorApp() {
        System.out.println("Creating window...");
        
        frame = new JFrame("V.E.C.T.O.R");
        frame.setSize(500, 600);
        frame.setLocation(200, 100);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // Create components
        frame.setLayout(new BorderLayout());
        
        // Header - cyan on blue
        JLabel header = new JLabel("V.E.C.T.O.R", SwingConstants.CENTER);
        header.setPreferredSize(new Dimension(500, 50));
        header.setBackground(new Color(50, 50, 150));
        header.setForeground(Color.CYAN);
        header.setOpaque(true);
        header.setFont(new Font("SansSerif", Font.BOLD, 28));
        frame.add(header, BorderLayout.NORTH);
        
        // Chat - white on black
        chat = new JTextArea();
        chat.setBackground(Color.BLACK);
        chat.setForeground(Color.WHITE);
        chat.setFont(new Font("SansSerif", Font.PLAIN, 16));
        chat.setEditable(false);
        chat.setText("V.E.C.T.O.R - AI Assistant\n\nType a question and press SEND or ENTER\n\n");
        chat.setCaretColor(Color.WHITE);
        
        JScrollPane scroll = new JScrollPane(chat);
        frame.add(scroll, BorderLayout.CENTER);
        
        // Bottom panel
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(Color.DARK_GRAY);
        
        input = new JTextField();
        input.setBackground(Color.DARK_GRAY);
        input.setForeground(Color.WHITE);
        input.setFont(new Font("SansSerif", Font.PLAIN, 16));
        input.setCaretColor(Color.WHITE);
        input.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        send = new JButton("SEND");
        send.setBackground(new Color(50, 100, 200));
        send.setForeground(Color.WHITE);
        send.setFont(new Font("SansSerif", Font.BOLD, 16));
        send.setPreferredSize(new Dimension(100, 0));
        
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(send, BorderLayout.EAST);
        frame.add(bottom, BorderLayout.SOUTH);
        
        // Action listeners
        send.addActionListener(e -> onSend());
        input.addActionListener(e -> onSend());
        
        // Show
        frame.setVisible(true);
        
        // Force repaint
        frame.validate();
        frame.repaint();
        
        System.out.println("Window displayed!");
    }

    private void onSend() {
        String q = input.getText().trim();
        if (q.isEmpty() || loading) return;
        
        chat.append("You: " + q + "\n");
        input.setText("");
        
        loading = true;
        send.setEnabled(false);
        
        chat.append("Thinking...\n");
        
        new Thread(() -> {
            try {
                String response = callAPI(q);
                chat.append("V.E.C.T.O.R: " + response + "\n\n");
            } catch (Exception e) {
                chat.append("ERROR: " + e.getMessage() + "\n\n");
            }
            
            loading = false;
            send.setEnabled(true);
            chat.setCaretPosition(chat.getText().length());
        }).start();
    }

    private String callAPI(String q) throws Exception {
        URL url = new URL("http://localhost:8080/api/ask");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        conn.getOutputStream().write(("{\"question\":\"" + q.replace("\"", "\\\"") + "\"}").getBytes());
        
        if (conn.getResponseCode() != 200) {
            throw new Exception("Error: " + conn.getResponseCode());
        }
        
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        
        String json = sb.toString();
        int start = json.indexOf("\"answer\"");
        if (start < 0) return "No answer";
        start = json.indexOf(":", start) + 1;
        int end = json.indexOf(",", start);
        if (end < 0) end = json.indexOf("}", start);
        
        return json.substring(start, end).replace("\"", "").trim();
    }

    public static void main(String[] args) {
        System.out.println("Starting V.E.C.T.O.R...");
        SwingUtilities.invokeLater(() -> new VectorApp());
    }
}