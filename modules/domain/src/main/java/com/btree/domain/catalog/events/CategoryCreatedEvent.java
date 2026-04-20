package com.btree.domain.catalog.events;



import com.btree.shared.domain.DomainEvent;

/**
 * Evento de domínio disparado quando uma nova categoria é criada.
 */
public class CategoryCreatedEvent extends DomainEvent {

    private final String categoryId;
    private final String name;
    private final String slug;

    public CategoryCreatedEvent(final String categoryId, final String name, final String slug) {
        super();
        this.categoryId = categoryId;
        this.name = name;
        this.slug = slug;
    }

    @Override
    public String getAggregateId() {
        return categoryId;
    }

    @Override
    public String getAggregateType() {
        return "Category";
    }

    @Override
    public String getEventType() {
        return "category.created";
    }

    public String getCategoryId() { return categoryId; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
}

