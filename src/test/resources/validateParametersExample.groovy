boolean call() {
    return validateParameters([:], [
            validationRule(param: 'name1').notNull().notEmpty().notRobot(),
            validationRule(param: 'name2').notNull().notEmpty().notAnyMethod(),
    ])
}
