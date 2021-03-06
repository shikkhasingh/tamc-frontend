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

package controllers

import errors._
import models._
import models.auth.{AuthenticatedUserRequest, PermanentlyAuthenticated}
import org.joda.time.LocalDate
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{CachingService, TimeService, TransferService, UpdateRelationshipService}
import test_utils._
import test_utils.data.{ConfirmationModelData, RelationshipRecordData}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.emailaddress.EmailAddress
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.partials.FormPartialRetriever
import uk.gov.hmrc.renderer.TemplateRenderer
import uk.gov.hmrc.time.TaxYearResolver

import scala.concurrent.Future

class UpdateRelationshipControllerTest extends ControllerBaseSpec {

  val mockRegistrationService: TransferService = mock[TransferService]
  val mockUpdateRelationshipService: UpdateRelationshipService = mock[UpdateRelationshipService]
  val mockCachingService: CachingService = mock[CachingService]
  val mockTimeService: TimeService = mock[TimeService]

  def controller(auth: AuthenticatedActionRefiner = instanceOf[AuthenticatedActionRefiner]): UpdateRelationshipController =
    new UpdateRelationshipController(
      messagesApi,
      auth,
      mockUpdateRelationshipService,
      mockRegistrationService,
      mockCachingService,
      mockTimeService
  )(instanceOf[TemplateRenderer], instanceOf[FormPartialRetriever])

  "History" should {
    "redirect to transfer" when {
      "has no active record, no historic and temporary authentication" in{
        when(mockUpdateRelationshipService.listRelationship(any())(any(), any()))
          .thenReturn(
            Future.successful(
              (RelationshipRecordList(None, None, None, activeRecord = false, historicRecord = false, historicActiveRecord = false), true)
            )
          )
        val result: Future[Result] = controller(instanceOf[MockTemporaryAuthenticatedAction]).history()(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(controllers.routes.TransferController.transfer().url)
      }
    }

    "redirect to how-it-works" when {
      "has no active record, no historic and permanent authentication" in {
        when(mockUpdateRelationshipService.listRelationship(any())(any(), any()))
          .thenReturn(
            Future.successful(
              (RelationshipRecordList(None, None, None, activeRecord = false, historicRecord = false, historicActiveRecord = false),true)
            )
          )
        val result: Future[Result] = controller().history()(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(controllers.routes.EligibilityController.howItWorks().url)
      }
    }

    "load change of circumstances page" when {
      "has some active record" in {
        when(mockUpdateRelationshipService.listRelationship(any())(any(), any()))
          .thenReturn(
            Future.successful((RelationshipRecordData.activeRelationshipRecordList, false))
          )
        when(mockTimeService.taxYearResolver)
          .thenReturn(TaxYearResolver)

        val result: Future[Result] = controller().history()(request)
        status(result) shouldBe OK
      }

      "has some historic record" in {
        when(mockUpdateRelationshipService.listRelationship(any())(any(), any()))
          .thenReturn(
            Future.successful((RelationshipRecordData.historicRelationshipRecordList,true))
          )
        when(mockTimeService.taxYearResolver)
          .thenReturn(TaxYearResolver)

        val result: Future[Result] = controller().history()(request)
        status(result) shouldBe OK
      }
    }
  }

  "makeChange" should {
    "return successful response" when {
      "a valid form is submitted" in {
        val request = FakeRequest().withFormUrlEncodedBody(
          "role" -> "some role",
          "endReason" -> "some end reason",
          "historicActiveRecord" -> "true"
        )
        val result = controller().makeChange()(request)
        status(result) shouldBe OK
      }
    }

    "redirect the user" when {
      "an invalid form with errors is submitted" in {
        val request = FakeRequest().withFormUrlEncodedBody("historicActiveRecord" -> "string")
        val result = controller().makeChange()(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.UpdateRelationshipController.history().url)
      }
    }
  }

  class UpdateRelationshipActionTest(endReason: String) {
    val request: Request[AnyContent] = FakeRequest().withFormUrlEncodedBody(
      "role" -> "some role",
      "endReason" -> endReason,
      "historicActiveRecord" -> "true",
      "creationTimestamp" -> "timestamp",
      "dateOfDivorce" -> new LocalDate(TaxYearResolver.currentTaxYear, 6, 12).toString()
    )
    when(mockCachingService.saveRoleRecord(any())(any(), any())).thenReturn("OK")
    when(mockUpdateRelationshipService.saveEndRelationshipReason(any())(any(), any()))
      .thenReturn(Future.successful(EndRelationshipReason("")))
    val result: Future[Result] = controller().updateRelationshipAction()(request)
    lazy val document: Document = Jsoup.parse(contentAsString(result))
  }

  "updateRelationshipAction" should {
    "return a success" when {
      "the end reason code is DIVORCE" in new UpdateRelationshipActionTest("DIVORCE") {
        status(result) shouldBe OK
        document.getElementsByTag("h1").first().text() shouldBe messagesApi("title.divorce")
      }

      "the end reason code is EARNINGS" in new UpdateRelationshipActionTest("EARNINGS") {
        status(result) shouldBe OK
        document.getElementsByTag("h1").first().text() shouldBe messagesApi("change.status.earnings.h1")
      }

      "the end reason code is BEREAVEMENT" in new UpdateRelationshipActionTest("BEREAVEMENT") {
        status(result) shouldBe OK
        document.getElementsByTag("h1").first().text() shouldBe messagesApi("change.status.bereavement.sorry")
      }
    }

    "redirect the user" when {
      "the end reason code is CANCEL" in new UpdateRelationshipActionTest("CANCEL") {
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(controllers.routes.UpdateRelationshipController.confirmCancel().url)
      }

      "the end reason code is REJECT" in new UpdateRelationshipActionTest("REJECT") {
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(controllers.routes.UpdateRelationshipController.confirmReject().url)
      }
    }

    "return a bad request" when {
      "an invalid form is submitted" in {
        val request = FakeRequest().withFormUrlEncodedBody("role" -> "ROLE", "historicActiveRecord" -> "string")
        val result: Future[Result] = controller().updateRelationshipAction()(request)
        status(result) shouldBe BAD_REQUEST
      }

      "an unrecognised end reason is submitted" in new UpdateRelationshipActionTest("DIVORCE_PY") {
        status(result) shouldBe BAD_REQUEST
      }
    }
  }

  "changeOfIncome" should {
    "return success" in {
      status(controller().changeOfIncome()(request)) shouldBe OK
    }
  }

  "bereavement" should {
    "return success" in {
      status(controller().bereavement()(request)) shouldBe OK
    }
  }

  "confirmYourEmailActionUpdate" should {
    "return a bad request" when {
      "an invalid form is submitted" in {
        val request = FakeRequest().withFormUrlEncodedBody("transferor-email" -> "not a real email")
        val result = controller().confirmYourEmailActionUpdate()(request)
        status(result) shouldBe BAD_REQUEST
      }
    }

    "redirect" when {
      "a successful form is submitted" in {
        val email = "example@example.com"
        val record = NotificationRecord(EmailAddress(email))
        when(mockRegistrationService.upsertTransferorNotification(ArgumentMatchers.eq(record))(any(), any()))
          .thenReturn(record)
        val request = FakeRequest().withFormUrlEncodedBody("transferor-email" -> email)
        val result = controller().confirmYourEmailActionUpdate()(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(controllers.routes.UpdateRelationshipController.confirmUpdate().url)
        verify(mockRegistrationService, times(1))
          .upsertTransferorNotification(ArgumentMatchers.eq(record))(any(), any())
      }
    }
  }

  "divorceYear" should {
    "return a success" when {
      "there is cache data returned" in {
        when(mockUpdateRelationshipService.getUpdateRelationshipCacheDataForDateOfDivorce(any(), any())).thenReturn(
          Some(UpdateRelationshipCacheData(None, Some(""), relationshipEndReasonRecord = Some(EndRelationshipReason("")), notification = None))
        )
        val result = controller().divorceYear()(request)

        status(result) shouldBe OK
      }
    }

    "return InternalServerError" when {
      "there is no cache data" in {
        when(mockUpdateRelationshipService.getUpdateRelationshipCacheDataForDateOfDivorce(any(), any())).thenReturn(None)
        status(controller().divorceYear()(request)) shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }

  "divorceSelectYear" should {
    "return bad request" when {
      "an invalid form is submitted" in {
        val request = FakeRequest().withFormUrlEncodedBody("role"-> "")
        val result = controller().divorceSelectYear()(request)
        status(result) shouldBe BAD_REQUEST
      }
    }

    "return a success" when {
      def request = FakeRequest().withFormUrlEncodedBody(
        "role"-> "some role",
        "endReason" -> "DIVORCE",
        "historicActiveRecord" -> "true",
        "creationTimeStamp" -> "timestamp",
        "dateOfDivorce.day" -> "1",
        "dateOfDivorce.month" -> "1",
        "dateOfDivorce.year" -> "2000"
      )

      "divorce date is valid" in {
        when(mockUpdateRelationshipService.isValidDivorceDate(any())(any(), any())).thenReturn(true)
        when(mockTimeService.getEffectiveUntilDate(any())).thenReturn(Some(LocalDate.now()))
        when(mockTimeService.getEffectiveDate(any())).thenReturn(LocalDate.now())
        val result = controller().divorceSelectYear()(request)
        lazy val document: Document = Jsoup.parse(contentAsString(result))

        status(result) shouldBe OK
        document.getElementsByTag("h1").first().text() shouldBe messagesApi("change.status.divorce.transferor.h1")
      }

      "divorce date is invalid" in {
        when(mockUpdateRelationshipService.isValidDivorceDate(any())(any(), any())).thenReturn(false)
        val result = controller().divorceSelectYear()(request)
        lazy val document: Document = Jsoup.parse(contentAsString(result))

        status(result) shouldBe OK
        document.getElementsByTag("h1").first().text() shouldBe messagesApi("change.status.divorce.date.invalid.h1")
      }
    }
  }

  "divorceAction" should {
    "return a bad request" when {
      "an invalid form is submitted" in {
        val request = FakeRequest().withFormUrlEncodedBody(
          "role" -> "some role",
          "endReason" -> "invalid end reason",
          "historicActiveRecord" -> "true",
          "creationTimeStamp" -> "timestamp",
          "dateOfDivorce.day" -> "1",
          "dateOfDivorce.month" -> "1",
          "dateOfDivorce.year" -> "2000"
        )
        val result = controller().divorceAction()(request)
        status(result) shouldBe BAD_REQUEST
      }
    }

    "redirect the user" when {
      "EndRelationshipReason is pulled from the cache" in {
        val request = FakeRequest().withFormUrlEncodedBody(
          "role" -> "some role",
          "endReason" -> "DIVORCE_CY",
          "historicActiveRecord" -> "true",
          "creationTimeStamp" -> "timestamp",
          "dateOfDivorce.day" -> "1",
          "dateOfDivorce.month" -> "1",
          "dateOfDivorce.year" -> "2000"
        )
        when(mockUpdateRelationshipService.saveEndRelationshipReason(any())(any(), any()))
          .thenReturn(EndRelationshipReason("DIVORCE_CY"))
        val result = controller().divorceAction()(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(controllers.routes.UpdateRelationshipController.confirmEmail().url)
      }
    }
  }

  "confirmEmail" should {
    "return a success" when {
      "an email is recovered from the cache" in {
        val email = "test@test.com"
        when(mockUpdateRelationshipService.getUpdateNotification(any(), any()))
          .thenReturn(Some(NotificationRecord(EmailAddress(email))))
        val result = controller().confirmEmail()(request)
        status(result) shouldBe OK
        val document = Jsoup.parse(contentAsString(result))
        document.getElementById("transferor-email").attr("value") shouldBe email
      }

      "no email is recovered from the cache" in {
        when(mockUpdateRelationshipService.getUpdateNotification(any(), any()))
          .thenReturn(None)
        val result = controller().confirmEmail()(request)
        status(result) shouldBe OK
      }
    }
  }

  "confirmReject" should {
    "return a success" when {
      "data is returned from the cache" in {
        val relationshipCacheData = Some(RelationshipRecordData.updateRelationshipCacheData)
        when(mockUpdateRelationshipService.getUpdateRelationshipCacheForReject(any(), any()))
          .thenReturn(relationshipCacheData)
        when(mockUpdateRelationshipService.getRelationship(ArgumentMatchers.eq(relationshipCacheData.get)))
          .thenReturn(RelationshipRecordData.activeRecord)
        when(mockUpdateRelationshipService.getEndDate(ArgumentMatchers.eq(relationshipCacheData.get.relationshipEndReasonRecord.get),
          ArgumentMatchers.eq(RelationshipRecordData.activeRecord)))
          .thenReturn(LocalDate.now())
        val result = controller().confirmReject()(request)
        status(result) shouldBe OK
      }
    }
  }

  "confirmCancel" should {
    "return a success" when {
      "end reason is successfully cached" in {
        val endReason = EndRelationshipReason(EndReasonCode.CANCEL)
        when(mockUpdateRelationshipService.saveEndRelationshipReason(ArgumentMatchers.eq(endReason))(any(), any()))
          .thenReturn(endReason)
        val result = controller().confirmCancel()(request)
        status(result) shouldBe OK
      }
    }
  }

  "getConfirmationInfoFromReason" should {
    "return a relationship end date, a relevant date and isEnded true" when {
      "the end reason code is Reject, the end date from relationship service is not blank" in {
        val relationshipEndDate = LocalDate.now()
        val endDate = LocalDate.now.minusDays(1)
        val endRelationshipReason = EndRelationshipReason(EndReasonCode.REJECT)

        when(mockUpdateRelationshipService.getRelationship(ArgumentMatchers.eq(RelationshipRecordData.updateRelationshipCacheData)))
          .thenReturn(RelationshipRecordData.activeRecord)
        when(mockUpdateRelationshipService.getRelationEndDate(ArgumentMatchers.eq(RelationshipRecordData.activeRecord)))
          .thenReturn(relationshipEndDate)
        when(mockUpdateRelationshipService.getEndDate(ArgumentMatchers.eq(endRelationshipReason), ArgumentMatchers.eq(RelationshipRecordData.activeRecord)))
          .thenReturn(endDate)

        val result = controller().getConfirmationInfoFromReason(endRelationshipReason, RelationshipRecordData.updateRelationshipCacheData)
        result shouldBe (true, Some(relationshipEndDate), Some(endDate))
      }
    }

    "return no relationship end date, a relevant date and isEnded false" when {
      "the end reason code is Reject, the end date from relationship service is blank" in {
        val endRelationshipReason = EndRelationshipReason(EndReasonCode.REJECT)
        val endDate = LocalDate.now.minusDays(1)

        when(mockUpdateRelationshipService.getRelationship(ArgumentMatchers.eq(RelationshipRecordData.updateRelationshipCacheData)))
          .thenReturn(RelationshipRecordData.activeRecordWithNoEndDate)
        when(mockUpdateRelationshipService.getEndDate(ArgumentMatchers.eq(endRelationshipReason), ArgumentMatchers.eq(RelationshipRecordData.activeRecordWithNoEndDate)))
          .thenReturn(endDate)

        val result = controller().getConfirmationInfoFromReason(endRelationshipReason, RelationshipRecordData.updateRelationshipCacheData)
        result shouldBe (false, None, Some(endDate))
      }

      "the end reason code is DIVORCE_PY" in {
        val endRelationshipReason = EndRelationshipReason(EndReasonCode.DIVORCE_PY)
        val date = LocalDate.now()
        when(mockTimeService.getEffectiveDate(ArgumentMatchers.eq(endRelationshipReason)))
          .thenReturn(date)

        val result = controller().getConfirmationInfoFromReason(endRelationshipReason, RelationshipRecordData.updateRelationshipCacheData)
        result shouldBe (false, None, Some(date))
      }

      "the end reason code is DIVORCE_CY" in {
        val endRelationshipReason = EndRelationshipReason(EndReasonCode.DIVORCE_CY)
        val date = LocalDate.now()
        when(mockTimeService.getEffectiveDate(ArgumentMatchers.eq(endRelationshipReason)))
          .thenReturn(date)

        val result = controller().getConfirmationInfoFromReason(endRelationshipReason, RelationshipRecordData.updateRelationshipCacheData)
        result shouldBe (false, None, Some(date))
      }

      "the end reason code is CANCEL" in {
        val endRelationshipReason = EndRelationshipReason(EndReasonCode.CANCEL)
        val date = LocalDate.now()
        when(mockTimeService.getEffectiveDate(ArgumentMatchers.eq(endRelationshipReason)))
          .thenReturn(date)

        val result = controller().getConfirmationInfoFromReason(endRelationshipReason, RelationshipRecordData.updateRelationshipCacheData)
        result shouldBe (false, None, Some(date))
      }
    }

  }

  "confirmUpdate" should {
    "return a success" when {
      "the relationship service successfully returns cache data and end relationship reason" in {
        when(mockUpdateRelationshipService.getConfirmationUpdateData(any(), any()))
          .thenReturn((ConfirmationModelData.updateRelationshipConfirmationModel, Some(RelationshipRecordData.updateRelationshipCacheData)))
        when(mockTimeService.getEffectiveDate(any()))
          .thenReturn(LocalDate.now())
        val result = controller().confirmUpdate()(request)
        status(result) shouldBe OK
      }
    }

    "return InternalServerError" when {
      "there is no cache data returned" in {
        when(mockUpdateRelationshipService.getConfirmationUpdateData(any(), any()))
          .thenReturn((ConfirmationModelData.updateRelationshipConfirmationModel, None))
        val result = controller().confirmUpdate()(request)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }

  "confirmUpdateAction" should {
    "redirct the user" when {
      "update relationship returns a future successful" in {
        when(mockUpdateRelationshipService.updateRelationship(any(), any())(any(), any(), any()))
          .thenReturn(RelationshipRecordData.notificationRecord)
        val result = controller().confirmUpdateAction()(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(controllers.routes.UpdateRelationshipController.finishUpdate().url)
      }
    }
  }

  "finishUpdate" should {
    "return a success" when {
      "an email is available" in {
        when(mockUpdateRelationshipService.getupdateRelationshipFinishedData(any())(any(), any()))
          .thenReturn((RelationshipRecordData.notificationRecord, EndRelationshipReason(EndReasonCode.REJECT)))

        val result = controller().finishUpdate()(request)
        status(result) shouldBe OK
      }
    }
  }

  "handleError" should {
    val auhtRequest: AuthenticatedUserRequest[_] = AuthenticatedUserRequest(
      request,
      PermanentlyAuthenticated,
      Some(ConfidenceLevel.L200),
      isSA = false,
      Some("GovernmentGateway"),
      Nino(TestData.Ninos.nino1)
    )

    "return internal server error" when {
      val errors = List(
        (new CacheMissingUpdateRecord, "technical.issue.para1"),
        (new CacheUpdateRequestNotSent, "technical.issue.para1"),
        (new CannotUpdateRelationship, "technical.issue.para1"),
        (new CitizenNotFound, "technical.cannot-find-details.para1"),
        (new BadFetchRequest, "technical.technical-error.para1"),
        (new TransferorNotFound, "transferor.not.found"),
        (new RecipientNotFound, "recipient.not.found.para1"),
        (new Exception, "technical.issue.para1")
      )

      for((error, message) <- errors) {
        s"a $error has been thrown" in {
          val result = controller().handleError(HeaderCarrier(), auhtRequest)(error)
          status(result) shouldBe INTERNAL_SERVER_ERROR
          val doc = Jsoup.parse(contentAsString(result))
          doc.getElementById("error").text() shouldBe messagesApi(message)
        }
      }
    }

    "redirect" when {
      "a errors.CacheRelationshipAlreadyUpdated exception has been thrown" in {
        val result = controller().handleError(HeaderCarrier(), auhtRequest)(new CacheRelationshipAlreadyUpdated)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(controllers.routes.UpdateRelationshipController.finishUpdate().url)
      }
    }
  }
}
