package io.github.astvvg.ats.common;

import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class PageRequests {

    private static final Sort ID_DESC = Sort.by(Sort.Direction.DESC, "id");

    private PageRequests() {}

    /**
     * Applies {@code defaultSort} when the client sent none, rejects properties outside {@code
     * allowed} with 400, and always ends with id desc so pages are stable on ties.
     */
    public static Pageable withSort(Pageable pageable, Set<String> allowed, Sort defaultSort) {
        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = defaultSort;
        } else {
            for (Sort.Order order : sort) {
                if (!allowed.contains(order.getProperty())) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Unsupported sort property: " + order.getProperty());
                }
            }
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.and(ID_DESC));
    }
}
