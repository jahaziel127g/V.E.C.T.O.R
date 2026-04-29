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
        System.out.println("Starting...");
        
        frame = new JFrame("V.E.C.T.O.R");
        frame.setSize(500, 600);
        frame.setLocation(100, 100);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        frame.setBackground(Color.BLACK);
        frame.getContentPane().setBackground(Color.BLACK);
        
        JPanel main = new JPanel(null);
        main.setBackground(Color.BLACK);
        
        JLabel header = new JLabel("V.E.C.T.O.R", JLabel.CENTER);
        header.setBounds(0, 0, 500, 40);
        header.setBackground(Color.BLUE);
        header.setForeground(Color.CYAN);
        header.setOpaque(true);
        header.setFont(new Font("Arial", Font.BOLD, 20));
        main.add(header);
        
        chat = new JTextArea();
        chat.setBounds(0, 40, 500, 420);
        chat.setBackground(Color.BLACK);
        chat.setForeground(Color.WHITE);
        chat.setEditable(false);
        chat.setFont(new Font("Arial", Font.PLAIN, 14));
        chat.setText("V.E.C.T.O.R - AI Assistant\n\nAsk me anything!\n");
        main.add(chat);
        
        input = new JTextField();
        input.setBounds(0, 460, 400, 40);
        input.setBackground(Color.DARK_GRAY);
        input.setForeground(Color.WHITE);
        input.setCaretColor(Color.WHITE);
        input.setFont(new Font("Arial", Font.PLAIN, 14));
        main.add(input);
        
        send = new JButton("SEND");
        send.setBounds(400, 460, 100, 40);
        send.setBackground(Color.BLUE);
        send.setForeground(Color.WHITE);
        send.setFont(new Font("Arial", Font.BOLD, 14));
        send.addActionListener(e -> onSend());
        main.add(send);
        
        input.addActionListener(e -> onSend());
        
        frame.add(main);
        frame.setVisible(true);
        
        System.out.println("Window ready!");
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
                chat.setCaretPosition(chat.getText().length());
            } catch (Exception e) {
                chat.append("ERROR: " + e.getMessage() + "\n");
            }
            
            loading = false;
            send.setEnabled(true);
        }).start();
    }

    private String callAPI(String question) throws Exception {
        URL url = new URL("http://localhost:8080/api/ask");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        String json = "{\"question\":\"" + question.replace("\"", "\\\"") + "\"}";
        conn.getOutputStream().write(json.getBytes());
        
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new Exception("Server error: " + code);
        }
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        
        String jsonResponse = response.toString();
        int answerStart = jsonResponse.indexOf("\"answer\"");
        if (answerStart < 0) return "No answer found";
        
        answerStart = jsonResponse.indexOf(":", answerStart) + 1;
        int answerEnd = jsonResponse.indexOf(",", answerStart);
        if (answerEnd < 0) answerEnd = jsonResponse.indexOf("}", answerStart);
        
        return jsonResponse.substring(answerStart, answerEnd).replace("\"", "").trim();
    }

    public static void main(String[] args) {
        new VectorApp();
    }
}