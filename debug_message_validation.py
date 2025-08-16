#!/usr/bin/env python3

import requests
import json

def test_null_message():
    # Start the server first
    base_url = "http://localhost:9922"
    
    # Create a topic first
    response = requests.post(f"{base_url}/api/topics", json={"name": "test-topic"})
    print(f"Create topic response: {response.status_code} - {response.text}")
    
    if response.status_code == 201:
        topic_data = response.json()
        topic_arn = topic_data["arn"]
        
        # Test with null message
        publish_data = {
            "topicArn": topic_arn,
            "message": None
        }
        
        response = requests.post(f"{base_url}/api/publish", 
                               json=publish_data,
                               headers={"Content-Type": "application/json"})
        
        print(f"Null message test response: {response.status_code} - {response.text}")
        
        # Test with missing message
        publish_data_no_message = {
            "topicArn": topic_arn
        }
        
        response = requests.post(f"{base_url}/api/publish", 
                               json=publish_data_no_message,
                               headers={"Content-Type": "application/json"})
        
        print(f"Missing message test response: {response.status_code} - {response.text}")

if __name__ == "__main__":
    test_null_message()
