package com.nexuslabs.vector.desktop;

import javax.swing.*;
import java.awt.*;

public class VectorApp extends JFrame {

    private JTextArea chat;
    private JTextField input;
    private JButton send;
    private boolean loading = false;

    public VectorApp() {
        super("V.E.C.T.O.R");
        System.out.println("Constructor started");
        
        // Force window to be visible and have content
        setSize(500, 600);
        setLocation(100, 100);
        
        // SIMPLE - no layouts, just put components in
        setLayout(null);
        setBackground(Color.BLACK);
        
        // Header label - blue text
        JLabel header = new JLabel("V.E.C.T.O.R");
        header.setBounds(0, 0, 500, 40);
        header.setBackground(Color.BLUE);
        header.setForeground(Color.CYAN);
        header.setOpaque(true);
        header.setFont(new Font("Arial", Font.BOLD, 24));
        add(header);
        
        // Chat area - black background, white text
        chat = new JTextArea();
        chat.setBounds(0, 40, 500, 400);
        chat.setBackground(Color.BLACK);
        chat.setForeground(Color.WHITE);
        chat.setFont(new Font("Arial", Font.PLAIN, 14));
        chat.setEditable(false);
        add(chat);
        
        // Input field
        input = new JTextField();
        input.setBounds(0, 440, 400, 40);
        input.setBackground(Color.DARK_GRAY);
        input.setForeground(Color.WHITE);
        input.setCaretColor(Color.WHITE);
        add(input);
        
        // Send button
        send = new JButton("SEND");
        send.setBounds(400, 440, 100, 40);
        send.setBackground(Color.BLUE);
        send.setForeground(Color.WHITE);
        send.addActionListener(e -> sendMessage());
        add(send);
        
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
        
        chat.setText("V.E.C.T.O.R\nHello! How can I help?\n\n");
        System.out.println("UI created");
    }

    private void sendMessage() {
        String q = input.getText();
        if (q.isEmpty()) return;
        
        chat.append("You: " + q + "\n");
        input.setText("");
        
        chat.append("Thinking...\n");
        
        new Thread(() -> {
            try {
                String r = callAPI(q);
                chat.append("V.E.C.T.O.R: " + r + "\n\n");
            } catch (Exception e) {
                chat.append("ERROR: " + e.getMessage() + "\n");
            }
        }).start();
    }

    private String callAPI(String q) throws Exception {
        java.net.URL url = new java.net.URL("http://localhost:8080/api/ask");
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        c.getOutputStream().write(("{\"question\":\"" + q + "\"}").getBytes());
        
        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        
        return sb.toString();
    }

    public static void main(String[] args) {
        System.out.println("Main called");
        new VectorApp();
    }
}