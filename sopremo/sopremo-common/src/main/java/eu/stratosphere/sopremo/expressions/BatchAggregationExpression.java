package eu.stratosphere.sopremo.expressions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.codehaus.jackson.JsonNode;

import eu.stratosphere.sopremo.EvaluationContext;
import eu.stratosphere.sopremo.JsonUtil;
import eu.stratosphere.sopremo.aggregation.AggregationFunction;

/**
 * Batch aggregates one stream of {@link JsonNode} with several {@link AggregationFunction}s.
 * 
 * @author Arvid Heise
 */
public class BatchAggregationExpression extends EvaluationExpression {
	/**
	 * 
	 */
	private static final long serialVersionUID = 39927024374509247L;

	private final List<Partial> partials;

	private transient JsonNode lastResult;

	private transient int lastInputCounter = Integer.MIN_VALUE;

	public BatchAggregationExpression(final AggregationFunction... functions) {
		this(Arrays.asList(functions));
	}

	public BatchAggregationExpression(final List<AggregationFunction> functions) {
		this.partials = new ArrayList<Partial>(functions.size());
		for (final AggregationFunction function : functions)
			this.partials.add(new Partial(function, EvaluationExpression.VALUE, this.partials.size()));
	}

	public EvaluationExpression add(final AggregationFunction function) {
		return this.add(function, EvaluationExpression.VALUE);
	}

	public EvaluationExpression add(final AggregationFunction function, final EvaluationExpression preprocessing) {
		final Partial partial = new Partial(function, preprocessing, this.partials.size());
		this.partials.add(partial);
		return partial;
	}

	@Override
	public JsonNode evaluate(final JsonNode node, final EvaluationContext context) {
		if (this.lastInputCounter == context.getInputCounter())
			return this.lastResult;

		this.lastInputCounter = context.getInputCounter();

		for (final Partial partial : this.partials)
			partial.getFunction().initialize();
		for (final JsonNode input : node)
			for (final Partial partial : this.partials)
				partial.getFunction().aggregate(partial.getPreprocessing().evaluate(input, context), context);

		final JsonNode[] results = new JsonNode[this.partials.size()];
		for (int index = 0; index < results.length; index++)
			results[index] = this.partials.get(index).getFunction().getFinalAggregate();

		return this.lastResult = JsonUtil.asArray(results);
	}

	private class Partial extends AggregationExpression {
		/**
		 * 
		 */
		private static final long serialVersionUID = -7597385785040647923L;

		private final int index;

		public Partial(final AggregationFunction function, final EvaluationExpression preprocessing, final int index) {
			super(function, preprocessing);
			this.index = index;
		}

		@Override
		public JsonNode evaluate(final JsonNode node, final EvaluationContext context) {
			return BatchAggregationExpression.this.evaluate(node, context).get(this.index);
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((partials == null) ? 0 : partials.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BatchAggregationExpression other = (BatchAggregationExpression) obj;
		if (partials == null) {
			if (other.partials != null)
				return false;
		} else if (!partials.equals(other.partials))
			return false;
		return true;
	}

	@Override
	protected void toString(StringBuilder builder) {
		builder.append("batch");
	}
}