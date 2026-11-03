package com.hzmwmt.trigger.http.client;

import com.hzmwmt.api.response.Response;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.infrastructure.gateway.netty.WebSocketTicketService;
import com.hzmwmt.types.enums.ResponseCode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ws")
@PreAuthorize("isAuthenticated()")
public class WebSocketTicketController {
    private final IAuthService authService;
    private final WebSocketTicketService ticketService;

    public WebSocketTicketController(
            IAuthService authService,
            WebSocketTicketService ticketService) {
        this.authService = authService;
        this.ticketService = ticketService;
    }

    @PostMapping("/ticket")
    public Response<WebSocketTicketResponse> issueTicket() {
        Long userId = authService.queryValidatedUser().getUserId();
        WebSocketTicketService.IssuedTicket ticket = ticketService.issue(userId);
        return Response.<WebSocketTicketResponse>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(new WebSocketTicketResponse(
                        ticket.value(),
                        WebSocketTicketService.SUBPROTOCOL,
                        ticket.expiresInSeconds()))
                .build();
    }

    public record WebSocketTicketResponse(
            String ticket,
            String protocol,
            long expiresInSeconds) {
    }
}
