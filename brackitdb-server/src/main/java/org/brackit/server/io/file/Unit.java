/*
 * [New BSD License]
 * Copyright (c) 2011-2012, Brackit Project Team <info@brackit.org>  
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Brackit Project Team nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.brackit.server.io.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.brackit.server.util.BitArrayWrapper;
import org.brackit.server.util.BitVector;

/**
 * 
 * Represents a logical unit within a container. Each block is assigned to
 * exactly one unit.
 * 
 * @author Martin Hiller
 * 
 */
public class Unit {

	private final RandomAccessFile file;
	
	public final BitVector blockTable;
	
	public Unit(File file, int initialSize) throws FileNotFoundException {
		
		this.file = new RandomAccessFile(file,
				Constants.FILE_MODE_UNSY);
		
		this.blockTable = new BitArrayWrapper(initialSize);
	}
	
	private Unit(RandomAccessFile file, BitVector blockTable) {
		this.file = file;
		this.blockTable = blockTable;
	}
	
	public static Unit fromFile(File file) throws IOException {
		
		RandomAccessFile f = new RandomAccessFile(file,
				Constants.FILE_MODE_UNSY);
		
		int length = f.readInt();
		
		byte[] b = new byte[length];		
		f.readFully(b);
		
		BitVector blockTable = BitArrayWrapper.fromBytes(b);
		
		return new Unit(f, blockTable);
	}
	
	public void sync() throws IOException {
		
		byte[] blockTableBytes = blockTable.toBytes();
		
		file.seek(0);
		file.writeInt(blockTableBytes.length);
		file.write(blockTableBytes);
		file.getFD().sync();
	}
	
	public void close() throws IOException {
		file.close();
	}
}