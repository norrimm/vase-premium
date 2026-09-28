package com.example.trade.notify;

import java.time.Duration;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.trade.config.NotifyProperties;
import com.example.trade.repository.NotificationRepository;

/** 同一銘柄・同一方向のシグナルを cooldown 内に再通知しない（F-07）。 */
@Component
public class DuplicateSuppressor {

    private final NotificationRepository notifications;
    private final Duration cooldown;

    public DuplicateSuppressor(NotificationRepository notifications, NotifyProperties props) {
        this.notifications = notifications;
        this.cooldown = props.cooldown();
    }

    /** 抑止すべきなら、その原因になった前回の送信時刻（epoch ms）を返す。 */
    public Optional<Long> blockingSentAt(String symbol, String signal, long now) {
        return notifications.findLastSentAt(symbol, signal)
                .filter(sentAt -> now - sentAt < cooldown.toMillis());
    }

    public Duration cooldown() {
        return cooldown;
    }
}
