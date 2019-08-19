def call() {
    return validateParameters([:], [
            validationRule(param: 'name1').notNull().notEmpty().notRobot(),
            validationRule(param: 'name1').notNull().notEmpty().notAnyMethod()
    ])
}
