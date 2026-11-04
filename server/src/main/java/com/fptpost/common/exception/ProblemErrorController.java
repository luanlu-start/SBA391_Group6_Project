package com.fptpost.common.exception;

import com.fptpost.common.response.ApiProblem;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProblemErrorController implements ErrorController {
    private final ProblemResponseFactory problems;

    @RequestMapping("${server.error.path:/error}")
    public ResponseEntity<ApiProblem> error(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = attribute instanceof Integer value && value >= 400 && value <= 599 ? value : 500;
        return problems.response(GlobalErrorCode.forStatus(status), status, request, List.of(), new HttpHeaders());
    }
}
