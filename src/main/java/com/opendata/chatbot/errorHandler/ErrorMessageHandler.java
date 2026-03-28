package com.opendata.chatbot.errorHandler;

import com.opendata.chatbot.errorHandler.entity.ErroeResponse;
import com.opendata.chatbot.errorHandler.entity.Status;
import com.opendata.chatbot.util.JsonConverter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ErrorMessageHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({ErrorMessage.class})
    public final String errorMessageHandler(ErrorMessage ex) {
        return JsonConverter.toJsonString(new ErroeResponse(new Status(ex.getCode(), ex.getMessage())));
    }
}
