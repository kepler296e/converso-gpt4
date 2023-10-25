package com.ceibotech.converso

/*
{
  "id": "chatcmpl-123",
  "object": "chat.completion",
  "created": 1677652288,
  "model": "gpt-3.5-turbo-0613",
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant",
      "content": "\n\nHello there, how may I assist you today?",
    },
    "finish_reason": "stop"
  }],
  "usage": {
    "prompt_tokens": 9,
    "completion_tokens": 12,
    "total_tokens": 21
  }
}
*/

class ResponseJson(
    val id: String,
    val `object`: String,
    val created: Int,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage
)

class Choice(
    val index: Int,
    val message: Message,
    val finish_reason: String
)

class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)