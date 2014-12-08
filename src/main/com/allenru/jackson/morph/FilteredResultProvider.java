package com.allenru.jackson.morph;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.WeakHashMap;

import com.fasterxml.jackson.databind.ser.BeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;


/**
 * A FilteredResultProvider is a Jackson2 FilterProvider for {@link MorphedResult} instances.
 * 
 * To use this class, you must register this with Jackson2 as a FilterProvider:
 * 
 * 		ObjectMapper mapper = new ObjectMapper();
 * 		mapper.setFilters(new FilteredResultProvider());  //or addFilters()
 * 
 */
@SuppressWarnings("deprecation")
public class FilteredResultProvider extends FilterProvider {

	static final String OUTER_FILTER_ID = "FilteredResultProvider-FilteredResult-OUTER";
	static final String INNER_FILTER_ID = "FilteredResultProvider-FilteredResult-INNER";
	
	/* 
	 * Caution, non-trivial coding ahead...  This is a WeakHashMap; it uses WeakReferences as
	 * keys so that the real key may be garbage collected without regard for their presence in 
	 * this map.
	 * 
	 * This is EXACTLY what we want, and its CRITICAL that this behave this way.  Here's why:
	 * First, the obvious reason, that if for some reason the Jackson thread processed the outer
	 * filter (and thus populating this Map) throws an exception, then the inner filter
	 * would not execute and the inserted key-value pair would never be removed.  That's a memory
	 * leak, and the last I checked those were bad.
	 * Second, and this is much more subtle but even more important...  The outer Map is keyed by
	 * thread because the primary object, the key in the second map, may not be unique!  Consider
	 * the use case where two threads both want to serialize a domain object.  That domain object
	 * happens to be immutable and pulled from a cache, thus each thread has a handle to the
	 * exact same instance.  Now, Thread 1 wants all of the attributes while Thread 2 lacks 
	 * security to see all.  Their allowed sets are different.  Without the first layer of the 
	 * map using the thread as the key, the two threads would collide, and both would see the
	 * same set of attributes (which set is non-deterministic and irrelevant.)  
	 */
	private WeakHashMap<Thread, WeakHashMap<Object, MorphedResult<?>>> allowedAttributesByThreadThenPrimaryResult = new WeakHashMap<>();
	
	/**
	 * See {@link FilterProvider} for details of this methods contract.
	 * 
	 * This implementation uses two filter id's to track and pass data between the two stages of filtering.
	 * The first outer filter is applied to the {@link MorphedResult} class itself, while the second inner filter
	 * is applied to the 'primaryResult' property of the MorphedResult instance.
	 * 
	 * When Jackson encounters an instance of MorphedResult, it calls this filter implementation to get a 
	 * PropertyFilter instance.  The MorphedResult is actually annotated well enough that no property filter
	 * is neede, BUT by intercepting the Jackson parsing process at this point, we're able to capture the 
	 * configured meta-data... the allowed and excluded set, the expansion data, etc.  We will need this
	 * in order to build the inner property filter.  This information is cached in association to the thread
	 * and primary result being serialized.
	 * 
	 * Now, when Jackson attempts to serialize the primary result attribute of a MorphedResult instance, it
	 * encounters the inner filter, and asks this method to resolve that into a PropertyFilter instance.  The
	 * only information we get with this call is a reference to the primary result instance, but in order to
	 * build the PropertyFilter, we need to know the meta-data stored on the MorphedResult that contains the 
	 * primary result (valueToFilter parameter.)  To do this, we go back to the cached meta-data that was stored
	 * when the thread went through the outer filter, and bam!  We now have the data needed to properly filter
	 * the primary result.
	 * 
	 * @see com.fasterxml.jackson.databind.ser.FilterProvider#findPropertyFilter(java.lang.Object, java.lang.Object)
	 */
	@Override
	public PropertyFilter findPropertyFilter(Object filterId, Object valueToFilter) {
		Thread currentThread = Thread.currentThread();
		if (OUTER_FILTER_ID.equals(filterId) && valueToFilter instanceof MorphedResult) {
			MorphedResult<?> filteredResult = (MorphedResult<?>) valueToFilter;
			WeakHashMap<Object, MorphedResult<?>> allowedAttributesPrimaryResult = allowedAttributesByThreadThenPrimaryResult.get(currentThread);
			if (allowedAttributesPrimaryResult == null) allowedAttributesByThreadThenPrimaryResult.put(currentThread, allowedAttributesPrimaryResult = new WeakHashMap<>());

			//TODO is this required or even right?  
			
			Object primaryResult = filteredResult.getPrimaryResult();
			if (primaryResult instanceof Collection<?>) {
				Collection<?> primaryResults = (Collection<?>)primaryResult;
				for (Object atomicResult : primaryResults) {
					allowedAttributesPrimaryResult.put(atomicResult, filteredResult);
				}
			}
			else if (primaryResult.getClass().isArray()) {
				int length = Array.getLength(primaryResult);
				for (int i = 0; i < length; i++) {
					allowedAttributesPrimaryResult.put(Array.get(primaryResult, i), filteredResult);
				}
			}
			else {
				allowedAttributesPrimaryResult.put(filteredResult.getPrimaryResult(), filteredResult);
			}
			return null;  //the object is already annotated to only have primaryResult.  No need to filter.
		}
		else if (INNER_FILTER_ID.equals(filterId)) {
			final MorphedResult<?> filterResult = allowedAttributesByThreadThenPrimaryResult.get(currentThread).remove(valueToFilter);
			if (filterResult == null) throw new IllegalStateException("Failed to relate filter data to target.  Unable to properly filter target.");
			return new SimpleBeanPropertyFilter() {
				@Override protected boolean include(BeanPropertyWriter writer) {return filterResult.isPermitted(writer.getName()) && !filterResult.isReplaced(writer.getName());}
				@Override protected boolean include(PropertyWriter writer)     {return filterResult.isPermitted(writer.getName()) && !filterResult.isReplaced(writer.getName());}
			};
		}
		return null;  //the filterId is not supported by this Filter Provider.
	}
	
	//Required but not used by Jackson's FilterProvider base class
	@Override 
	public BeanPropertyFilter findFilter(Object filterId) {
		throw new UnsupportedOperationException("This deprecated method is not called by the latest version of Jackson.");
	}
}
