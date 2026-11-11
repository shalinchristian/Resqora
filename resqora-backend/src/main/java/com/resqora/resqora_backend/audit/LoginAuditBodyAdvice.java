package com.resqora.resqora_backend.audit;

import com.resqora.resqora_backend.dto.LoginRequest;
import com.resqora.resqora_backend.dto.VerifyEmailRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

/** Extract only the typed email before @Valid; never cache or parse the raw request body. */
@ControllerAdvice
public class LoginAuditBodyAdvice extends RequestBodyAdviceAdapter {
    @Override
    public boolean supports(MethodParameter parameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return targetType == LoginRequest.class || targetType == VerifyEmailRequest.class;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (body instanceof LoginRequest login) {
            LoginAudit.identify(login.email(), null);
        } else if (body instanceof VerifyEmailRequest verify) {
            LoginAudit.identify(verify.email(), null);
        }
        return body;
    }
}
