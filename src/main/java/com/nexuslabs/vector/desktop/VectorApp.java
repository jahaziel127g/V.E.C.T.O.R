package com.nexuslabs.vector.desktop;

import javax.swing.*;
import java.awt.*;

public class VectorApp {

    public static void main(String[] args) {
        System.out.println("Starting...");
        
        // Create frame manually
        JFrame frame = new JFrame("V.E.C.T.O.R");
        frame.setSize(500, 600);
        frame.setLocation(100, 100);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // Force black background at frame level
        frame.setBackground(Color.BLACK);
        frame.getContentPane().setBackground(Color.BLACK);
        
        // Create black panel
        JPanel main = new JPanel(null);
        main.setBackground(Color.BLACK);
        
        // Header - cyan
        JLabel header = new JLabel("V.E.C.T.O.R", JLabel.CENTER);
        header.setBounds(0, 0, 500, 40);
        header.setBackground(Color.BLUE);
        header.setForeground(Color.CYAN);
        header.setOpaque(true);
        header.setFont(new Font("Arial", Font.BOLD, 20));
        main.add(header);
        
        // Chat - black background
        JTextArea chat = new JTextArea();
        chat.setBounds(0, 40, 500, 420);
        chat.setBackground(Color.BLACK);
        chat.setForeground(Color.WHITE);
        chat.setEditable(false);
        chat.setFont(new Font("Arial", Font.PLAIN, 14));
        chat.setText("V.E.C.T.O.R - AI Assistant\n\nAsk me anything!\n");
        main.add(chat);
        
        // Input - dark gray
        JTextField input = new JTextField();
        input.setBounds(0, 460, 400, 40);
        input.setBackground(Color.DARK_GRAY);
        input.setForeground(Color.WHITE);
        input.setCaretColor(Color.WHITE);
        main.add(input);
        
        // Button - blue
        JButton send = new JButton("SEND");
        send.setBounds(400, 460, 100, 40);
        send.setBackground(Color.BLUE);
        send.setForeground(Color.WHITE);
        main.add(send);
        
        frame.add(main);
        frame.setVisible(true);
        
        System.out.println("Window shown");
    }
}