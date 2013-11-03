package com.ociweb.jfast.field.integer;

import com.ociweb.jfast.FASTAccept;
import com.ociweb.jfast.FASTProvide;
import com.ociweb.jfast.NullAdjuster;
import com.ociweb.jfast.ReadWriteEntry;
import com.ociweb.jfast.ValueDictionaryEntry;
import com.ociweb.jfast.field.Field;
import com.ociweb.jfast.primitive.PrimitiveReader;
import com.ociweb.jfast.primitive.PrimitiveWriter;
import com.ociweb.jfast.read.FASTError;
import com.ociweb.jfast.read.FASTException;
import com.ociweb.jfast.read.FieldTypeReadWrite;
import com.ociweb.jfast.read.ReadEntry;
import com.ociweb.jfast.write.WriteEntry;

public final class FieldIntUConstant extends Field {

	private final int id;
	private final int intValue;
	private final int repeat;
	
	public FieldIntUConstant(int id, ValueDictionaryEntry valueDictionaryEntry) {

		this.id = id;
		//next assignment	
		this.intValue = valueDictionaryEntry.intValue;
		this.repeat = 1;
		
		if (valueDictionaryEntry.isNull) {
			throw new FASTException(FASTError.MANDATORY_CONSTANT_NULL);
	    }
		
	}

	public final void reader(PrimitiveReader reader, FASTAccept visitor) {
		visitor.accept(id, intValue);
		//end of reader
	}

	public void writer(PrimitiveWriter writer, FASTProvide provider) {
		//end of writer
	}
	
	public void reset() {
		//end of reset
	}

}
