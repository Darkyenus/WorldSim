/* *****************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.utils;

/** A binary heap that stores nodes which each have a long value and are sorted either lowest first or highest first. The
 * {@link Node} class can be extended to store additional information.
 * @author Nathan Sweet */
@SuppressWarnings({"unchecked", "unused", "rawtypes"})
public class LongBinaryHeap<T extends LongBinaryHeap.Node> {
	public int size;

	private Node[] nodes;
	private final boolean isMaxHeap;

	public LongBinaryHeap() {
		this(16, false);
	}

	public LongBinaryHeap(int capacity, boolean isMaxHeap) {
		this.isMaxHeap = isMaxHeap;
		nodes = new Node[Math.max(capacity, 1)];
	}

	/** Sets the node's value and adds it to the heap. The node should not already be in the heap. */
	public T add (T node, long value) {
		node.value = value;
		// Expand if necessary.
		if (size == nodes.length) {
			Node[] newNodes = new Node[size << 1];
			System.arraycopy(nodes, 0, newNodes, 0, size);
			nodes = newNodes;
		}
		// Insert at end and bubble up.
		nodes[size] = node;
		up(size++);
		return node;
	}

	/** Returns true if the heap contains the specified node.
	 * @param identity If true, == comparison will be used. If false, .equals() comparison will be used. */
	public boolean contains (T node, boolean identity) {
		if (node == null) throw new IllegalArgumentException("node cannot be null.");
		if (identity) {
			for (Node n : nodes)
				if (n == node) return true;
		} else {
			for (Node other : nodes)
				if (other.equals(node)) return true;
		}
		return false;
	}

	/** Returns the first item in the heap. This is the item with the lowest value (or highest value if this heap is configured as
	 * a max heap). */
	public T peek () {
		if (size == 0) throw new IllegalStateException("The heap is empty.");
		return (T)nodes[0];
	}

	/** Returns the first item in the heap.
	 * This is the item with the lowest value (or highest value if this heap is configured as a max heap).
	 * Returns null if empty. */
	public T peekOrNull () {
		return (T)nodes[0];
	}

	/** Removes the first item in the heap and returns it. This is the item with the lowest value (or highest value if this heap is
	 * configured as a max heap). */
	public T pop () {
		Node[] nodes = this.nodes;
		Node removed = nodes[0];
		nodes[0] = nodes[--size];
		nodes[size] = null;
		if (size > 0) down(0);
		return (T)removed;
	}

	/** Returns true if the heap has one or more items. */
	public boolean notEmpty () {
		return size > 0;
	}

	/** Returns true if the heap is empty. */
	public boolean isEmpty () {
		return size == 0;
	}

	public void clear () {
		Node[] nodes = this.nodes;
		for (int i = 0, n = size; i < n; i++)
			nodes[i] = null;
		size = 0;
	}

	private void up (int index) {
		Node[] nodes = this.nodes;
		Node node = nodes[index];
		long value = node.value;
		while (index > 0) {
			int parentIndex = (index - 1) >> 1;
			Node parent = nodes[parentIndex];
			if (value < parent.value ^ isMaxHeap) {
				nodes[index] = parent;
				index = parentIndex;
			} else
				break;
		}
		nodes[index] = node;
	}

	private void down (int index) {
		Node[] nodes = this.nodes;
		int size = this.size;

		Node node = nodes[index];
		long value = node.value;

		while (true) {
			int leftIndex = 1 + (index << 1);
			if (leftIndex >= size) break;
			int rightIndex = leftIndex + 1;

			// Always has a left child.
			Node leftNode = nodes[leftIndex];
			long leftValue = leftNode.value;

			// May have a right child.
			Node rightNode;
			long rightValue;
			if (rightIndex >= size) {
				rightNode = null;
				rightValue = isMaxHeap ? Long.MIN_VALUE : Long.MAX_VALUE;
			} else {
				rightNode = nodes[rightIndex];
				rightValue = rightNode.value;
			}

			// The smallest of the three values is the parent.
			if (leftValue < rightValue ^ isMaxHeap) {
				if (leftValue == value || (leftValue > value ^ isMaxHeap)) break;
				nodes[index] = leftNode;
				index = leftIndex;
			} else {
				if (rightValue == value || (rightValue > value ^ isMaxHeap)) break;
				nodes[index] = rightNode;
				index = rightIndex;
			}
		}

		nodes[index] = node;
	}

	@Override
	public boolean equals (Object obj) {
		if (!(obj instanceof LongBinaryHeap)) return false;
		LongBinaryHeap other = (LongBinaryHeap)obj;
		if (other.size != size) return false;
		Node[] nodes1 = this.nodes, nodes2 = other.nodes;
		for (int i = 0, n = size; i < n; i++)
			if (nodes1[i].value != nodes2[i].value) return false;
		return true;
	}

	public int hashCode () {
		int h = 1;
		for (int i = 0, n = size; i < n; i++)
			h = h * 31 + Long.hashCode(nodes[i].value);
		return h;
	}

	public String toString () {
		if (size == 0) return "[]";
		Node[] nodes = this.nodes;
		StringBuilder buffer = new StringBuilder(32);
		buffer.append('[');
		buffer.append(nodes[0].value);
		for (int i = 1; i < size; i++) {
			buffer.append(", ");
			buffer.append(nodes[i].value);
		}
		buffer.append(']');
		return buffer.toString();
	}

	/** A binary heap node.
	 * @author Nathan Sweet */
	static public class Node {
		long value;

		public final long getValue() {
			return value;
		}

		public String toString () {
			return Long.toString(value);
		}
	}
}
