package com.nexuslabs.vector.desktop;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class VectorApp extends Frame implements ActionListener {

    private TextArea chat = new TextArea();
    private TextField input = new TextField();
    private Button send = new Button("SEND");
    private boolean loading = false;
    private boolean floating = true;

    static {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("sun.awt.disableGraphicDoubleBuffer", "true");
    }

    public VectorApp() {
        super("V.E.C.T.O.R");
        
        setSize(480, 580);
        setLayout(new BorderLayout(0, 0));
        
        // Header
        Panel header = new Panel(new FlowLayout(FlowLayout.LEFT));
        header.setBackground(new Color(30, 30, 80));
        Label title = new Label("V.E.C.T.O.R");
        title.setForeground(new Color(0, 200, 255));
        title.setFont(new Font("Arial", Font.BOLD, 24));
        header.add(title);
        add(header, BorderLayout.NORTH);
        
        // Chat
        chat.setEditable(false);
        chat.setBackground(Color.BLACK);
        chat.setForeground(Color.WHITE);
        chat.setFont(new Font("Arial", Font.PLAIN, 14));
        add(chat, BorderLayout.CENTER);
        
        // Bottom
        Panel bottom = new Panel(new BorderLayout(5, 0));
        bottom.setBackground(new Color(30, 30, 60));
        
        input.setBackground(new Color(40, 40, 70));
        input.setForeground(Color.WHITE);
        input.setFont(new Font("Arial", Font.PLAIN, 14));
        
        send.setBackground(new Color(50, 80, 180));
        send.setForeground(Color.WHITE);
        send.setFont(new Font("Arial", Font.BOLD, 14));
        send.addActionListener(this);
        
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(send, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
        
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { System.exit(0); }
        });
        
        chat.setText("V.E.C.T.O.R - AI Assistant\n\nAsk me anything!\n");
        
        setVisible(true);
    }

    public void actionPerformed(ActionEvent e) {
        String q = input.getText().trim();
        if (q.isEmpty() || loading) return;
        
        chat.append("You: " + q + "\n");
        input.setText("");
        
        loading = true;
        send.setEnabled(false);
        
        new Thread(() -> {
            try {
                String r = callAPI(q);
                chat.append("V.E.C.T.O.R: " + r + "\n\n");
            } catch (Exception ex) {
                chat.append("ERROR: " + ex.getMessage() + "\n\n");
            }
            loading = false;
        }).start();
    }

    private String callAPI(String q) throws Exception {
        URL url = new URL("http://localhost:8080/api/ask");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        
        c.getOutputStream().write(("{\"question\":\"" + q.replace("\"", "\\\"") + "\"}").getBytes());
        
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String l;
        while ((l = r.readLine()) != null) sb.append(l);
        r.close();
        
        String json = sb.toString();
        int start = json.indexOf("\"answer\"");
        if (start < 0) return "No answer";
        start = json.indexOf(":", start) + 1;
        int end = json.indexOf(",", start);
        if (end < 0) end = json.indexOf("}", start);
        return json.substring(start, end).replace("\"", "").trim();
    }

    public static void main(String[] args) {
        new VectorApp();
    }
}