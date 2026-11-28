package io.github.astvvg.ats.common;

import java.util.List;
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
        Sort sort = checkedSort(pageable, allowed, defaultSort);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.and(ID_DESC));
    }

    /**
     * Same as {@link #withSort}, but ties are broken by id in the direction of the first sort
     * order, so an ascending list stays ascending on ties.
     */
    public static Pageable withSortTiedById(
            Pageable pageable, Set<String> allowed, Sort defaultSort) {
        Sort sort = checkedSort(pageable, allowed, defaultSort);
        Sort.Direction direction = sort.iterator().next().getDirection();
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                sort.and(Sort.by(direction, "id")));
    }

    /** Rejects with 400 a sort property outside {@code allowed} or a page no query can reach. */
    public static void check(Pageable pageable, Set<String> allowed) {
        // JPA takes the first result as an int; beyond that the query fails with a 500.
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            throw new InvalidFieldException("page", "está fuera de rango");
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw unsupportedSort(order.getProperty());
            }
        }
    }

    private static Sort checkedSort(Pageable pageable, Set<String> allowed, Sort defaultSort) {
        check(pageable, allowed);
        Sort sort = pageable.getSort();
        return sort.isUnsorted() ? defaultSort : sort;
    }

    private static ResponseStatusException unsupportedSort(String property) {
        ResponseStatusException ex =
                new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Unsupported sort property: " + property);
        ex.getBody()
                .setProperty(
                        "errors",
                        List.of(
                                new GlobalExceptionHandler.FieldViolation(
                                        "sort", "propiedad de ordenamiento no admitida")));
        return ex;
    }
}
