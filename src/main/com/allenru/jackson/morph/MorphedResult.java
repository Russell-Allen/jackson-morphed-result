package com.allenru.jackson.morph;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * A MorphedResult is a wrapper class specially annotated to transparently add, remove, replace and
 * otherwise filter properties from view when being rendered using Jackson2's {@link ObjectMapper}.
 * 
 * This class assumes Jackson2 based serialization.  It is annotated using Jackson2
 * annotations such that this class will effectively disappear, leaving the primary result
 * and any expansion data, all filtered by the allowed and excluded attributes settings.
 * 
 * Attribute permission can be expressed as an allowed set (exclude anything else) or as an
 * exclude set (include anything else.)  It's also possible to provide both an allowed and
 * an exclude set, but this is functionally equivalent to just allowing the attributes that
 * are distinct in the allowed list (ie, the result of allowed - except.)  If both sets are
 * null, then no attributes are filtered.
 * 
 * New attributes can be added to a result via expansion.  Existing values can be replaced via
 * replacement.  In either case, both the expanded and replaced attributes are subject to the 
 * allowed and excluded attribute configuration (name based.)
 * 
 * NOTE, this class works in conjunction with {@link FilteredResultProvider} which MUST be 
 * registered with Jackson2's {@link ObjectMapper}.  If this is missed, then no filtering
 * will occur.  See {@link FilteredResultProvider} for example of registering the filter provider.
 * 
 * NOTE, Jackson2 applies filtering before unwrapping.  What this means is that the primary result 
 * object that is being wrapped should not have @JsonUnwrapped properties.  Classes that use that
 * annotation won't be filtered as one might expect.
 * 
 */
@JsonFilter(FilteredResultProvider.OUTER_FILTER_ID)
public class MorphedResult<P> {
	
	private @JsonUnwrapped @JsonFilter(FilteredResultProvider.INNER_FILTER_ID) P primaryResult;
	
	private @JsonIgnore Set<String> allowedAttributes;  //null implies allow all
	private @JsonIgnore Set<String> excludedAttributes; //null implies exclude none
	private @JsonIgnore Set<String> replacedAttributes; //null implies no replacements
	private @JsonIgnore Map<String, Object> expansionData;  //null implies no expansion data


	public MorphedResult(P primaryResult) {
		assert(primaryResult != null);
		this.primaryResult = primaryResult;
		//Jackson doesn't handle stacked unwrapping and filtering very well.  We must collapse the stack.
		if (primaryResult instanceof MorphedResult) {
			@SuppressWarnings("unchecked")
			MorphedResult<P> other = (MorphedResult<P>) primaryResult;
			this.primaryResult = other.primaryResult;
			//clone collections to prevent write through
			this.allowedAttributes = other.allowedAttributes == null ? null : new HashSet<String>(other.allowedAttributes);
			this.excludedAttributes = other.excludedAttributes == null ? null : new HashSet<String>(other.excludedAttributes);
			this.replacedAttributes = other.replacedAttributes == null ? null : new HashSet<String>(other.replacedAttributes);
			this.expansionData = other.expansionData == null ? null : new HashMap<String, Object>(other.expansionData);
		}
	}
	

	@JsonUnwrapped
	public P getPrimaryResult() {
		return primaryResult;
	}

	/**
	 * Returns the set, possibly null, of attribute names that are explicitly declared as allowed.
	 * A null set implies that all properties are implicitly allowed.  Note, even if a property is
	 * declared as allowed, it may also be declared as excluded.  In this conflicting case, this 
	 * implementation will exclude the property.
	 */
	@JsonIgnore
	public Set<String> getAllowedAttributes() {
		return allowedAttributes;
	}
	
	/**
	 * Add the passed in property name to the allowed list.
	 */
	public void allowAttribute(String attributeName) {
		if (allowedAttributes == null) allowedAttributes = new HashSet<>();
		allowedAttributes.add(attributeName);
	}
	
	/**
	 * Add the passed in property names to the allowed list.
	 */
	public void allowAttributes(String... attributeNames) {
		if (allowedAttributes == null) allowedAttributes = new HashSet<>();
		allowedAttributes.addAll(Arrays.asList(attributeNames));
	}
	
	/**
	 * Add the passed in property names to the allowed list.
	 */
	public void allowAttributes(Collection<String> attributeNames) {
		if (allowedAttributes == null) allowedAttributes = new HashSet<>();
		allowedAttributes.addAll(attributeNames);
	}
	
	/**
	 * Returns the set, possibly null, of explicitly excluded attribute names.
	 * If null, then no attributes are explicitly excluded.  However, if an
	 * allowed set exists, then only properties in the allowed list and not the
	 * exclude list will be serialized.
	 */
	@JsonIgnore
	public Set<String> getExcludedAttributes() {
		return excludedAttributes;
	}
	
	/**
	 * Add the passed in property name to the exclude list.
	 */
	public void excludeAttribute(String attributeName) {
		if (excludedAttributes == null) excludedAttributes = new HashSet<>();
		excludedAttributes.add(attributeName);
	}
	
	/**
	 * Add the passed in property names to the exclude list.
	 */
	public void excludeAttributes(String... attributeNames) {
		if (excludedAttributes == null) excludedAttributes = new HashSet<>();
		excludedAttributes.addAll(Arrays.asList(attributeNames));
	}
	
	/**
	 * Add the passed in property names to the exclude list.
	 */
	public void excludeAttributes(Collection<String> attributeNames) {
		if (excludedAttributes == null) excludedAttributes = new HashSet<>();
		excludedAttributes.addAll(attributeNames);
	}
	
	
	/**
	 * Returns the set, possibly null, of replaced attributes, by name.
	 * If null, then no attributes have been replaced.  Replaced attributes
	 * can be thought of as an exclude followed by adding (expand) an
	 * attribute with the same name.  In fact, the only reason that a separate
	 * replaced set exists, is to allow an attribute name to be reused in 
	 * this (replace) manner.
	 */
	@JsonIgnore
	public Set<String> getReplacedAttributes() {
		return replacedAttributes;
	}
	
	/**
	 * Adds the passed in data as an attribute to the primary result under the
	 * specified name.  It is assumed that the attribute name provided is an
	 * existing property that this new value will mask.  Note, the allow and 
	 * exclude behavior still applies.  Thus, if the named attribute is excluded,
	 * then the replacement will not be serialized.
	 * Replacing a non-existent attribute will behave identical to calling
	 * addExpansionData with the same arguments.
	 */
	public void replaceAttribute(String attributeName, Object data) {
		if (replacedAttributes == null) replacedAttributes = new HashSet<>();
		replacedAttributes.add(attributeName);
		addExpansionData(attributeName, data);
	}
	
	/**
	 * Returns true if and only if the specified attribute name will be serialized.
	 * See code for logic.
	 */
	@JsonIgnore
	public boolean isPermitted(String attribute) {
		boolean permitted = (allowedAttributes == null || allowedAttributes.contains(attribute))
				&& (excludedAttributes == null || !excludedAttributes.contains(attribute));
		return permitted;
	}
	
	/**
	 * Returns true if and only if the named attribute has been replaced.  Note,
	 * this is independent of the allowed and exclude logic (see isPermitted).
	 */
	@JsonIgnore
	public boolean isReplaced(String attribute) {
		return (replacedAttributes != null && replacedAttributes.contains(attribute));
	}
	
	
	/**
	 * Returns a map of attribute names (key) to objects (value) as specified by
	 * Jackson's {@link JsonAnyGetter} annotation.  This method is where the
	 * expanded attributes are exposed to Jackson for serialization.
	 * Note, expanded attributes must be permitted (see isPermitted) according to
	 * the allowed and excluded configuration.
	 */
	@JsonAnyGetter
	public Map<String, ?> getExpansionData() {
		if (expansionData == null) {
			return Collections.<String, Object>emptyMap();
		}
		else {
			Iterator<Entry<String, Object>> expansionIterator = expansionData.entrySet().iterator();
			while (expansionIterator.hasNext()) {
				Entry<String, Object> expansionEntry = expansionIterator.next();
				if (!isPermitted(expansionEntry.getKey())) expansionIterator.remove();
			}
			return expansionData;
		}
	}
	
	/**
	 * Add the specified data as an attribute under the specified name to the primary
	 * result, as if it were a property on the primary result.
	 */
	public void addExpansionData(String key, Object data) {
		if (expansionData == null) expansionData = new HashMap<>();
		expansionData.put(key, data);
	}
	
	
	@Override
	public String toString() {
		return "primaryResult: "+primaryResult
				+"\n"+"allowedAttributes: "+allowedAttributes
				+"\n"+"excludedAttributes: "+excludedAttributes
				+"\n"+"replacedAttributes: "+replacedAttributes
				+"\n"+"expansionData: "+expansionData;
	}
}
