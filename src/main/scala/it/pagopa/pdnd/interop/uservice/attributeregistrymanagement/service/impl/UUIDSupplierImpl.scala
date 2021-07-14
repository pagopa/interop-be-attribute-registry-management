package it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.service.impl

import java.util.UUID

class UUIDSupplierImpl extends UUIDSupplier {
  override def get: UUID = UUID.randomUUID()
}
