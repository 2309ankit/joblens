package com.ankit.joblens.intelligence;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

@Component
public class HtmlTextCleaner {

    public String clean(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String text = Jsoup.parseBodyFragment(html).body().text()
                .replace('\u00a0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return text.isEmpty() ? null : text;
    }
}
