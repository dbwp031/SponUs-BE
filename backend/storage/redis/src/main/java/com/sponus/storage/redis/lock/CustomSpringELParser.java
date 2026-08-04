package com.sponus.storage.redis.lock;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * @DistributedLock 의 key(SpEL)를 메서드 파라미터로 평가한다.
 */
public class CustomSpringELParser {

	private CustomSpringELParser() {
	}

	public static Object getDynamicValue(String[] parameterNames, Object[] args, String key) {
		ExpressionParser parser = new SpelExpressionParser();
		StandardEvaluationContext context = new StandardEvaluationContext();

		for (int i = 0; i < parameterNames.length; i++) {
			context.setVariable(parameterNames[i], args[i]);
		}

		return parser.parseExpression(key).getValue(context, Object.class);
	}
}
