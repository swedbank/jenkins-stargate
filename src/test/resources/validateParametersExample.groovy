boolean call() {
    return validateParameters([:], [
            validationRule('name1').required().notNull().notEmpty().notRobot(),
            validationRule('name2').optional().notNull().notEmpty().notAnyMethod(),
    ])
}
