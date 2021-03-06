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

package connectors

import config.ApplicationConfig
import errors.ErrorResponseStatus.{BAD_REQUEST, CITIZEN_NOT_FOUND, TRANSFEROR_NOT_FOUND}
import errors.{BadFetchRequest, CitizenNotFound, TransferorNotFound}
import models._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.config.ServicesConfig
import utils.WSHttp

import scala.concurrent.{ExecutionContext, Future}

object MarriageAllowanceConnector extends MarriageAllowanceConnector with ServicesConfig {
  override def httpGet = WSHttp
  override def httpPost = WSHttp
  override def httpPut = WSHttp
  override def marriageAllowanceUrl = ApplicationConfig.marriageAllowanceUrl
}

trait MarriageAllowanceConnector {

  def httpGet: HttpGet
  def httpPost: HttpPost
  def httpPut: HttpPut
  def marriageAllowanceUrl: String

  def listRelationship(transferorNino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[RelationshipRecordWrapper] =
    httpGet.GET[RelationshipRecordStatusWrapper](s"${marriageAllowanceUrl}/paye/${transferorNino}/list-relationship") map {
      case RelationshipRecordStatusWrapper(relationshipRecordWrapper, ResponseStatus("OK"))      => relationshipRecordWrapper
      case RelationshipRecordStatusWrapper(_, ResponseStatus(TRANSFEROR_NOT_FOUND)) => throw TransferorNotFound()
      case RelationshipRecordStatusWrapper(_, ResponseStatus(CITIZEN_NOT_FOUND))    => throw CitizenNotFound()
      case RelationshipRecordStatusWrapper(_, ResponseStatus(BAD_REQUEST))          => throw BadFetchRequest()
    }

  def getRecipientRelationship(transferorNino: Nino, recipientData: RegistrationFormInput)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    httpPost.POST(s"${marriageAllowanceUrl}/paye/${transferorNino}/get-recipient-relationship", body = recipientData)

  def createRelationship(transferorNino: Nino, data: CreateRelationshipRequestHolder, journey: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    httpPut.PUT(s"${marriageAllowanceUrl}/paye/${transferorNino}/create-multi-year-relationship/${journey}", data)
  }

  def updateRelationship(transferorNino: Nino, data: UpdateRelationshipRequestHolder)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    httpPut.PUT(s"${marriageAllowanceUrl}/paye/${transferorNino}/update-relationship", data)
}
