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
    
    private Color bg = new Color(30, 30, 40);
    private Color inputBg = new Color(50, 50, 70);
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
        } catch (Exception e) {}
    }

    public VectorApp() {
        super("V.E.C.T.O.R");
        logger.info("=== APP STARTED ===");
        
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(500, 600);
        setLocationRelativeTo(null);
        
        // Only set colors, no complex look and feel
        setBackground(bg);
        getContentPane().setBackground(bg);
        
        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(40, 40, 60));
        JLabel title = new JLabel("V.E.C.T.O.R");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setForeground(new Color(0, 200, 255));
        header.add(title, BorderLayout.WEST);
        header.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        
        // Chat
        chat = new JTextArea();
        chat.setEditable(false);
        chat.setBackground(bg);
        chat.setForeground(botColor);
        chat.setFont(new Font("Arial", Font.PLAIN, 14));
        
        // Input
        input = new JTextField();
        input.setBackground(inputBg);
        input.setForeground(textColor);
        input.setCaretColor(textColor);
        
        send = new JButton("Send");
        send.setFont(new Font("Arial", Font.BOLD, 14));
        
        JPanel bottom = new JPanel(new BorderLayout(5, 0));
        bottom.setBackground(new Color(40, 40, 60));
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(send, BorderLayout.EAST);
        bottom.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        add(header, BorderLayout.NORTH);
        add(new JScrollPane(chat), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        
        // One-time event
        send.addActionListener(e -> onSend());
        input.addActionListener(e -> onSend());
        
        setVisible(true);
        appendChat("Hello! I'm V.E.C.T.O.R.\nHow can I help?");
        
        logger.info("APP READY");
    }

    private void onSend() {
        String q = input.getText().trim();
        if (q.isEmpty() || loading) return;
        
        logger.info("USER: " + q);
        appendChat("You: " + q);
        input.setText("");
        loading = true;
        send.setText("...");
        input.setEnabled(false);
        
        new Thread(() -> {
            try {
                String r = callAPI(q);
                logger.info("GOT: " + r.substring(0, Math.min(50, r.length())));
                SwingUtilities.invokeLater(() -> {
                    appendChat("V.E.C.T.O.R: " + r);
                    loading = false;
                    send.setText("Send");
                    input.setEnabled(true);
                });
            } catch (Exception e) {
                logger.severe("ERROR: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    appendChat("ERROR: " + e.getMessage());
                    loading = false;
                    send.setText("Send");
                    input.setEnabled(true);
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
        c.getOutputStream().write(("{\"question\":\"" + q.replace("\"", "\\\"") + "\"}").getBytes());
        
        if (c.getResponseCode() != 200) {
            throw new Exception("Code: " + c.getResponseCode());
        }
        
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        
        int i = sb.indexOf("\"answer\"");
        if (i < 0) return "No answer";
        i = sb.indexOf(":", i) + 1;
        int end = sb.indexOf(",", i);
        if (end < 0) end = sb.indexOf("}", i);
        return sb.substring(i, end).replace("\"", "").trim();
    }

    private void appendChat(String txt) {
        chat.append(txt + "\n\n");
        chat.setCaretPosition(chat.getText().length());
    }

    public static void main(String[] args) {
        logger.info("MAIN");
        SwingUtilities.invokeLater(() -> new VectorApp());
    }
}