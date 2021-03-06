/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.emailaddress

import scala.util.matching.Regex
import play.api.data.FormError
import play.api.data.Forms.of
import play.api.data.Mapping
import play.api.data.format.Formatter
import play.api.data.validation.Constraint
import play.api.data.validation.Constraints
import play.api.data.validation.Invalid
import play.api.data.validation.Valid
import play.api.data.validation.ValidationError

object PlayFormFormatter {

  private def nonEmptyTrimmer(error: String = "error.required"): Mapping[String] =
    of(new Formatter[String] {
      def unbind(key: String, value: String): Map[String, String] = Map(key -> value)
      def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] = {
        data.get(key).map { _.trim }.filterNot(_.isEmpty()).toRight(Seq(FormError(key, error)))
      }
    })

  /**
   * Constructs a simple mapping for email address field (using a [[uk.gov.hmrc.emailaddress.EmailAddress]] type behind).
   *
   * @example
   * {{{
   * Form("email" ->
   *   emailAddress(
   *     errorRequired = <optional custom error message>,
   *     errorEmail    = <optional custom error message>))
   * }}}
   *
   * @param errorEmail 'this field has invalid format' error message with default value `"error.email"`
   * @param errorRequired 'this field is required' error message with default value `"error.required"`
   */
  def emailAddress(errorRequired: String = "error.required", errorEmail: String = "error.email"): Mapping[EmailAddress] =
    nonEmptyTrimmer(error = errorRequired).
      verifying(error = errorEmail, constraint = EmailAddress.isValid(_)).
      transform[EmailAddress](EmailAddress(_), _.value)

  /**
   * Constructs a simple mapping for email address field (using a [[uk.gov.hmrc.emailaddress.EmailAddress]] type behind).
   *
   * @example
   * {{{
   * Form("email" -> emailAddress)
   * }}}
   *
   * @see [[uk.gov.hmrc.emailaddress.PlayFormFormatter.emailAddress(String,String)]]
   */
  def emailAddress: Mapping[EmailAddress] = emailAddress()

  /**
   * Defines a regular expression constraint for [[uk.gov.hmrc.emailaddress.EmailAddress]] values
   *
   * @param error error message with default value `"error.pattern"`
   * @param name constraint's name with default value `"constraint.pattern"`
   */
  def emailPattern(regex: => Regex, name: String = "constraint.pattern", error: String = "error.pattern"): Constraint[EmailAddress] =
    Constraint[EmailAddress](name) {
      email =>
        Constraints.pattern(regex = regex, error = error)(email.value)
    }

  /**
   * Defines a maximum length constraint for [[uk.gov.hmrc.emailaddress.EmailAddress]] values
   *
   * @param error error message with default value `"error.maxLength(maxLength)"`
   * @param name constraint's name with default value `"constraint.maxLength(maxLength)"`
   */
  def emailMaxLength(maxLength: Int, name: String = "constraint.maxLength", error: String = "error.maxLength"): Constraint[EmailAddress] =
    Constraint[EmailAddress](name, maxLength) {
      email =>
        if (email == null) Invalid(ValidationError(error, maxLength))
        else if (email.value.size <= maxLength) Valid
        else Invalid(ValidationError(error, maxLength))
    }
}
