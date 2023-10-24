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

class ResponseJson {
    var id: String? = null
    var `object`: String? = null
    var created: Int? = null
    var model: String? = null
    var choices: List<Choice>? = null
    var usage: Usage? = null
}

class Choice {
    var index: Int? = null
    var message: Message? = null
    var finish_reason: String? = null
}

class Usage {
    var prompt_tokens: Int? = null
    var completion_tokens: Int? = null
    var total_tokens: Int? = null
}