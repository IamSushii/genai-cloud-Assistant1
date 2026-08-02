-- Create the database if it doesn't already exist
CREATE DATABASE IF NOT EXISTS genai_assistant;

-- Select the database for use
USE genai_assistant;

-- Create the chat history table to persist multi-turn conversations
CREATE TABLE IF NOT EXISTS chat_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    user_message TEXT NOT NULL,
    bot_response TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
