/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.stream.app.tensorflow.processor.logisticregression;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.springframework.cloud.stream.app.tensorflow.processor.TensorTupleConverter.TF_SHAPE;
import static org.springframework.cloud.stream.app.tensorflow.processor.TensorTupleConverter.TF_VALUE;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.tensorflow.Tensor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.app.tensorflow.processor.TensorTupleConverter;
import org.springframework.cloud.stream.app.tensorflow.processor.TensorflowOutputConverter;
import org.springframework.cloud.stream.app.tensorflow.processor.TensorflowProcessorConfiguration;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.cloud.stream.test.binder.MessageCollector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.tuple.Tuple;
import org.springframework.tuple.TupleBuilder;

/**
 * Integration Tests for TensorflowProcessor.
 *
 * Uses the example model from the TensorFlow Core tutorial (https://www.tensorflow.org/get_started/get_started#complete_program)
 * The model is exported as protobuf file (linear_regression_graph.proto) using this snipped
 * <code>
 *   RUN_DIR = os.path.abspath(os.path.curdir)
 *   minimal_graph = convert_variables_to_constants(sess, sess.graph_def, ['add'])
 *   tf.train.write_graph(minimal_graph, RUN_DIR, 'linear_regression_graph.proto', as_text=False)
 *   tf.train.write_graph(minimal_graph, RUN_DIR, 'linear_regression.txt', as_text=True)
 * </code>
 *
 * The linear_regression.txt provides detail graph model description.
 *
 * The computational graph ('add' = W * 'Placeholder' + b) takes an input (called 'Placeholder' of type FLOAT)
 * and computes output (called 'add' of type FLOAT). The 'W' and 'b' are the trained variables.
 *
 * @author Christian Tzolov
 */
@SuppressWarnings("SpringJavaAutowiringInspection")
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {
				"tensorflow.modelLocation=classpath:tensorflow/model/linear_regression_graph.proto",
				"tensorflow.outputName=add"
		}
)
@DirtiesContext
public abstract class LinearRegressionTensorflowProcessorIntegrationTests {

	@Autowired
	protected Processor channels;

	@Autowired
	protected MessageCollector messageCollector;

	@TestPropertySource(properties = {
			"tensorflow.resultHeader="
	})
	public static class LinearRegressionResultInPayloadTests extends LinearRegressionTensorflowProcessorIntegrationTests {
		@Test
		public void testEvaluationFloatInput() {
			testEvaluation(0.7f);
		}

		@Test
		public void testEvaluationWithTensorInput() {
			testEvaluation(Tensor.create(0.7f));
		}

		@Test
		public void testEvaluationWithTupleInput() {
			testEvaluation(TensorTupleConverter.toTuple(Tensor.create(0.7f)));
		}

		@Test(expected = MessageHandlingException.class)
		public void testEvaluationIncorrectTupleInput() {
			Tuple incompleteInputTuple = TupleBuilder.tuple()
					//	missing data type
					.put(TF_SHAPE, new long[0])
					.put(TF_VALUE, new byte[0])
					.build();
			testEvaluation(incompleteInputTuple);
		}

		private void testEvaluation(Object input) {

			Map<String, Object> inMap = new HashMap<>();
			inMap.put("Placeholder", input);

			Message<?> inputMessage = MessageBuilder
					.withPayload(inMap)
					.setHeader("passThroughHeaderName", "passThroughHeaderValue")
					.build();

			channels.input().send(inputMessage);

			Message<?> outputMessage = messageCollector.forChannel(channels.output()).poll();

			assertThat((String) outputMessage.getHeaders().get("passThroughHeaderName"),
					equalTo("passThroughHeaderValue"));
			assertThat((Float) outputMessage.getPayload(), equalTo(0.29999298f));
		}
	}

	@TestPropertySource(properties = {
			"tensorflow.resultHeader=myheader"
	})
	public static class LinearRegressionResultInHeaderTests extends LinearRegressionTensorflowProcessorIntegrationTests {
		@Test
		public void testEvaluationFloatInput() {
			testEvaluation(0.7f);
		}

		@Test
		public void testEvaluationWithTensorInput() {
			testEvaluation(Tensor.create(0.7f));
		}

		@Test
		public void testEvaluationWithTupleInput() {
			testEvaluation(TensorTupleConverter.toTuple(Tensor.create(0.7f)));
		}

		@Test(expected = MessageHandlingException.class)
		public void testEvaluationIncorrectTupleInput() {
			Tuple incompleteInputTuple = TupleBuilder.tuple()
					//	missing data type
					.put(TF_SHAPE, new long[0])
					.put(TF_VALUE, new byte[0])
					.build();
			testEvaluation(incompleteInputTuple);
		}

		private void testEvaluation(Object input) {

			Map<String, Object> inMap = new HashMap<>();
			inMap.put("Placeholder", input);

			Message<?> inputMessage = MessageBuilder
					.withPayload(inMap)
					.setHeader("passThroughHeader", "passThroughHeaderValue")
					.build();

			channels.input().send(inputMessage);

			Message<?> outputMessage = messageCollector.forChannel(channels.output()).poll();

			assertThat("Original Message Payload must be preserver",
					(Map<String, Object>) outputMessage.getPayload(), equalTo(inMap));

			assertThat((String) outputMessage.getHeaders().get("passThroughHeader"),
					equalTo("passThroughHeaderValue"));

			assertThat("Inference result must be stored in the header[myheader]",
					(Float) outputMessage.getHeaders().get("myheader"), equalTo(0.29999298f));
		}
	}

	/**
	 * Read input data from Message's header
	 */
	@TestPropertySource(properties = {
			"tensorflow.inputExpression=headers[myInputData]"
	})
	public static class LinearRegressionInputExpressionTests extends LinearRegressionTensorflowProcessorIntegrationTests {
		@Test
		public void testEvaluationFloatInput() {
			testEvaluation(0.7f);
		}

		@Test
		public void testEvaluationWithTensorInput() {
			testEvaluation(Tensor.create(0.7f));
		}

		@Test
		public void testEvaluationWithTupleInput() {
			testEvaluation(TensorTupleConverter.toTuple(Tensor.create(0.7f)));
		}

		@Test(expected = MessageHandlingException.class)
		public void testEvaluationIncorrectTupleInput() {
			Tuple incompleteInputTuple = TupleBuilder.tuple()
					//	missing data type
					.put(TF_SHAPE, new long[0])
					.put(TF_VALUE, new byte[0])
					.build();
			testEvaluation(incompleteInputTuple);
		}

		private void testEvaluation(Object input) {

			Map<String, Object> inMap = new HashMap<>();
			inMap.put("Placeholder", input);

			Message<?> inputMessage = MessageBuilder
					.withPayload("Dummy Payload")
					.setHeader("myInputData", inMap)
					.setHeader("passThroughHeaderName", "passThroughHeaderValue")
					.build();

			channels.input().send(inputMessage);

			Message<?> outputMessage = messageCollector.forChannel(channels.output()).poll();

			assertThat((String) outputMessage.getHeaders().get("passThroughHeaderName"),
					equalTo("passThroughHeaderValue"));
			assertThat((Float) outputMessage.getPayload(), equalTo(0.29999298f));

			assertThat((String) inputMessage.getPayload(), equalTo("Dummy Payload"));
		}
	}

	/**
	 * Obtain an input data from Payload Tuple
	 */
	@TestPropertySource(properties = {
			"tensorflow.inputExpression=payload.testTupleValue"
	})
	public static class LinearRegressionInputExpressionTupleTests extends LinearRegressionTensorflowProcessorIntegrationTests {
		@Test
		public void testEvaluationFloatInput() {
			testEvaluation(0.7f);
		}

		@Test
		public void testEvaluationWithTensorInput() {
			testEvaluation(Tensor.create(0.7f));
		}

		@Test
		public void testEvaluationWithTupleInput() {
			testEvaluation(TensorTupleConverter.toTuple(Tensor.create(0.7f)));
		}

		@Test(expected = MessageHandlingException.class)
		public void testEvaluationIncorrectTupleInput() {
			Tuple incompleteInputTuple = TupleBuilder.tuple()
					//	missing data type
					.put(TF_SHAPE, new long[0])
					.put(TF_VALUE, new byte[0])
					.build();
			testEvaluation(incompleteInputTuple);
		}

		private void testEvaluation(Object input) {

			Map<String, Object> inMap = new HashMap<>();
			inMap.put("Placeholder", input);

			Tuple inTuple = TupleBuilder.tuple().of("testTupleValue", inMap);
			Message<?> inputMessage = MessageBuilder
					.withPayload(inTuple)
					.build();

			channels.input().send(inputMessage);

			Message<?> outputMessage = messageCollector.forChannel(channels.output()).poll();

			assertThat((Float) outputMessage.getPayload(), equalTo(0.29999298f));

			assertThat((Tuple) inputMessage.getPayload(), equalTo(inTuple));
		}
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import(TensorflowProcessorConfiguration.class)
	public static class LogisticRegressionTensorflowProcessorApplication {

		@Bean
		public TensorflowOutputConverter tensorflowOutputConverter() {
			return new TensorflowOutputConverter<Object>() {
				@Override
				public Object convert(Tensor tensor, Map<String, Object> processorContext) {
					float[] outputValue = new float[1];
					tensor.copyTo(outputValue);
					return outputValue[0];
				}
			};
		}
	}
}
