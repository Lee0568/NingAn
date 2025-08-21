package net.thekingofduck.loki.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.stereotype.Component;

@Getter
@Setter
@ToString
@Component
public class CanvasEnity {
    private Integer number;
    private String canvasId;
}
