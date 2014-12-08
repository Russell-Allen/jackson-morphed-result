# Jackson MorphedResult
### Add, remove, and otherwise manipulate the properties of an object serialized with Jackson, at runtime without modifying the target object.

## Overview

This small library lets you wrap an object so that you can 'virtually' add, remove, and replace the properties that will be serialized by <a href="http://wiki.fasterxml.com/JacksonHome">Jackson</a>.  The wrapped object is not actually modified, and no special annotation or code is required within the classes being wrapped.  Wrapping of an object as well as configuration of which properties are added, removed, etc. can all be done at runtime.

## Usage

Simple example:
```java
		Credentials credentials = new Credentials();  //A simple POJO with two properties...
		credentials.setUsername("Bob");
		credentials.setPassword("password123");
		
		ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
		mapper.setFilters(new FilteredResultProvider());
		
		//Without MorphedResult...
		System.out.println(mapper.writeValueAsString(credentials));
//		{
//		  "username" : "Bob",
//		  "password" : "password123"
//		}
		
		MorphedResult<?> securedCredentials = new MorphedResult<>(credentials);
		securedCredentials.excludeAttribute("password");

		//Now, wrapped and excluding the password...
		System.out.println(mapper.writeValueAsString(securedCredentials));
//		{
//		  "username" : "Bob"
//		}
```

Two key points of this example: (a) the MorphedResult object itself disappears from the serialized view, and (b) don't forget to register the FilteredResultProvider with the ObjectMapper.

### Allowing and Excluding Properties

You may exclude specific properties as shown in the example above.  If instead, you wish to exclude all properties except for a known set, then simply use the allowAttribute(name) function to add the known set of permitted properties.  While you can use both exclude and allow together, its simpler and more logical to only use the allowed for the subset that are both allowed and not excluded.

### Adding and Replacing Properties

You may add properties by calling addExpansionData(name, data), passing the name that the added data should be exposed as and the data to add.  Note, this information is stored in the MorphedResult instance.  The primary result is not modified.

If the property already exists on the primary result but you wish to change it, then use the replaceAttribute(name, data) method to virtually replace the value associated to that property name.  Again, the primary result remains unmodified.

## Nuances

Collections - If you wish to morph a collection of objects, you will need to recreate the collection with each entry wrapped with a MorphedResult.

Nesting - MorphedResult instances will work anywhere in an object tree that is being serialized.  However, you must ensure that the MorphedResult instance is in the object tree, which may require using the morphing ability up the tree to the root.  Consider this case...

Person class contains Address, and we want to hide the street property of an address.  However, we've been given a Person instance that we can't modify.  Thus, we can't set the Person.address property to point to a MorphedResult<Address> instance.  So how do we solve this?  Simple (ok, maybe not that simple):

```java
	//given person (an instance of Person class)...
	MorphedResult<Person> morphedPerson = new MorphedResult(person);
	MorphedResult<Address> morphedAddress = new MorphedResult(person.address);
	morphedPerson.replaceAttribute("address", morphedAddress);
	return morphedPerson;
```
That example is simplified so that I can illustrate how you can use replacement to walk the morphing up the object tree.  A more realistic but also more complicated case, where you don't know the object tree structure or it changes from call to call, requires some introspection and potentially a little recursion to solve.  If there's enough interest, I can implement this and add it as path based deep property support.

## Known Issues

None that I am aware of.  If you find something, please let me know!  Feedback is welcome.

