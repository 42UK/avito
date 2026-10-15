package ru.avitodeals.product.application;

public interface AttributeExtractor {

	ExtractedAttributes extract(String title, String description);

	ExtractedAttributes extract(String title, String description, ExtractedCondition declaredCondition);
}
