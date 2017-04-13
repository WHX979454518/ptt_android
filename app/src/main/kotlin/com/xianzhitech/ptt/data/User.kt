package com.xianzhitech.ptt.data

import io.requery.Entity
import io.requery.Key
import io.requery.Persistable
import io.requery.Table

@Entity
@Table(name = "users")
interface User : Persistable {
    @get:Key
    val id : String

    val name : String

    val avatar : String?

    val priority : Int

    val phoneNumber : String?

    val enterpriseId : String

    val enterpriseName : String

}